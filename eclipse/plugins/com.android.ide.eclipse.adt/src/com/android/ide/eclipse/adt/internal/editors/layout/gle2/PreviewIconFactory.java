/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.eclipse.adt.internal.editors.layout.gle2;

import static com.android.ide.common.layout.LayoutConstants.ANDROID_URI;
import static com.android.ide.eclipse.adt.AndroidConstants.DOT_PNG;
import static com.android.ide.eclipse.adt.AndroidConstants.DOT_XML;

import com.android.ide.common.rendering.LayoutLibrary;
import com.android.ide.common.rendering.api.Capability;
import com.android.ide.common.rendering.api.LayoutLog;
import com.android.ide.common.rendering.api.RenderSession;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.ide.common.rendering.api.SessionParams.RenderingMode;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.descriptors.DocumentDescriptor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.ElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.ViewMetadataRepository;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.ViewMetadataRepository.RenderMode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiDocumentNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData;
import com.android.util.Pair;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.RGB;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Factory which can provide preview icons for android views of a particular SDK and
 * editor's configuration chooser
 */
public class PreviewIconFactory {
    private static final String TAG_ITEM = "item"; //$NON-NLS-1$
    private static final String ATTR_COLOR = "color";  //$NON-NLS-1$
    private PaletteControl mPalette;
    private RGB mBackground;
    private RGB mForeground;
    private File mImageDir;

    private static final String PREVIEW_INFO_FILE = "preview.properties"; //$NON-NLS-1$

    public PreviewIconFactory(PaletteControl palette) {
        mPalette = palette;
    }

    /**
     * Resets the state in the preview icon factory such that it will re-fetch information
     * like the theme and SDK (the icons themselves are cached in a directory across IDE
     * session though)
     */
    public void reset() {
        mImageDir = null;
        mBackground = null;
        mForeground = null;
    }

    /**
     * Deletes all the persistent state for the current settings such that it will be regenerated
     */
    public void refresh() {
        File imageDir = getImageDir(false);
        if (imageDir != null && imageDir.exists()) {
            File[] files = imageDir.listFiles();
            for (File file : files) {
                file.delete();
            }
            imageDir.delete();
            reset();
        }
    }

    /**
     * Returns an image descriptor for the given element descriptor, or null if no image
     * could be computed. The rendering parameters (SDK, theme etc) correspond to those
     * stored in the associated palette.
     *
     * @param desc the element descriptor to get an image for
     * @return an image descriptor, or null if no image could be rendered
     */
    public ImageDescriptor getImageDescriptor(ElementDescriptor desc) {
        File imageDir = getImageDir(false);
        if (!imageDir.exists()) {
            render();
        }
        File file = new File(imageDir, getFileName(desc));
        if (file.exists()) {
            try {
                return ImageDescriptor.createFromURL(file.toURI().toURL());
            } catch (MalformedURLException e) {
                AdtPlugin.log(e, "Could not create image descriptor for %s", file);
            }
        }

        return null;
    }

    /**
     * Partition the elements in the document according to their rendering preferences;
     * elements that should be skipped are removed, elements that should be rendered alone
     * are placed in their own list, etc
     *
     * @param document the document containing render fragments for the various elements
     * @return
     */
    private List<List<Element>> partitionRenderElements(Document document) {
        List<List<Element>> elements = new ArrayList<List<Element>>();

        List<Element> shared = new ArrayList<Element>();
        Element root = document.getDocumentElement();
        elements.add(shared);

        ViewMetadataRepository repository = ViewMetadataRepository.get();

        NodeList children = root.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                String fqn = repository.getFullClassName(element);
                assert fqn.length() > 0 : element.getNodeName();
                RenderMode renderMode = repository.getRenderMode(fqn);
                if (renderMode == RenderMode.ALONE) {
                    elements.add(Collections.singletonList(element));
                } else if (renderMode == RenderMode.NORMAL) {
                    shared.add(element);
                } else {
                    assert renderMode == RenderMode.SKIP;
                }
            }
        }

        return elements;
    }

    /**
     * Renders ALL the widgets and then extracts image data for each view and saves it on
     * disk
     */
    private boolean render() {
        File imageDir = getImageDir(true);

        GraphicalEditorPart editor = mPalette.getEditor();
        LayoutEditor layoutEditor = editor.getLayoutEditor();
        LayoutLibrary layoutLibrary = editor.getLayoutLibrary();
        Integer overrideBgColor = null;
        if (layoutLibrary != null) {
            if (layoutLibrary.supports(Capability.CUSTOM_BACKGROUND_COLOR)) {
                Pair<RGB, RGB> themeColors = getColorsFromTheme();
                RGB bg = themeColors.getFirst();
                RGB fg = themeColors.getSecond();
                if (bg != null) {
                    storeBackground(imageDir, bg, fg);
                    overrideBgColor = Integer.valueOf(ImageUtils.rgbToInt(bg, 0xFF));
                }
            }
        }

        ViewMetadataRepository repository = ViewMetadataRepository.get();
        Document document = repository.getRenderingConfigDoc();

        if (document == null) {
            return false;
        }

        // Construct UI model from XML
        AndroidTargetData data = layoutEditor.getTargetData();
        DocumentDescriptor documentDescriptor;
        if (data == null) {
            documentDescriptor = new DocumentDescriptor("temp", null/*children*/);//$NON-NLS-1$
        } else {
            documentDescriptor = data.getLayoutDescriptors().getDescriptor();
        }
        UiDocumentNode model = (UiDocumentNode) documentDescriptor.createUiNode();
        model.setEditor(layoutEditor);
        model.setUnknownDescriptorProvider(editor.getModel().getUnknownDescriptorProvider());

        Element documentElement = document.getDocumentElement();
        List<List<Element>> elements = partitionRenderElements(document);
        for (List<Element> elementGroup : elements) {
            // Replace the document elements with the current element group
            while (documentElement.getFirstChild() != null) {
                documentElement.removeChild(documentElement.getFirstChild());
            }
            for (Element element : elementGroup) {
                documentElement.appendChild(element);
            }

            model.loadFromXmlNode(document);

            RenderSession session = null;
            try {
                LayoutLog logger = new RenderLogger("palette");
                // Important to get these sizes large enough for clients that don't support
                // RenderMode.FULL_EXPAND such as 1.6
                int width = 200;
                int height = 2000;
                Set<UiElementNode> expandNodes = Collections.<UiElementNode>emptySet();
                RenderingMode renderingMode = RenderingMode.FULL_EXPAND;

                session = editor.render(model, width, height, expandNodes,
                        overrideBgColor, true /*no decorations*/, logger,
                        renderingMode);

            } catch (Throwable t) {
                // If there are internal errors previewing the components just revert to plain
                // icons and labels
                continue;
            }

            if (session != null) {
                if (session.getResult().isSuccess()) {
                    BufferedImage image = session.getImage();
                    if (image != null && image.getWidth() > 0 && image.getHeight() > 0) {

                        // Fallback for older platforms where we couldn't do background rendering
                        // at the beginning of this method
                        if (mBackground == null) {
                            Pair<RGB, RGB> themeColors = getColorsFromTheme();
                            RGB bg = themeColors.getFirst();
                            RGB fg = themeColors.getSecond();

                            if (bg == null) {
                                // Just use a pixel from the rendering instead.
                                int p = image.getRGB(image.getWidth() - 1, image.getHeight() - 1);
                                // However, in this case we don't trust the foreground color
                                // even if one was found in the themes; pick one that is guaranteed
                                // to contrast with the background
                                bg = ImageUtils.intToRgb(p);
                                if (ImageUtils.getBrightness(ImageUtils.rgbToInt(bg, 255)) < 128) {
                                    fg = new RGB(255, 255, 255);
                                } else {
                                    fg = new RGB(0, 0, 0);
                                }
                            }
                            storeBackground(imageDir, bg, fg);
                            assert mBackground != null;
                        }

                        List<ViewInfo> viewInfoList = session.getRootViews();
                        if (viewInfoList != null && viewInfoList.size() > 0) {
                            // We don't render previews under a <merge> so there should
                            // only be one root.
                            ViewInfo firstRoot = viewInfoList.get(0);
                            int parentX = firstRoot.getLeft();
                            int parentY = firstRoot.getTop();
                            List<ViewInfo> infos = firstRoot.getChildren();
                            for (ViewInfo info : infos) {
                                Object cookie = info.getCookie();
                                if (!(cookie instanceof UiElementNode)) {
                                    continue;
                                }
                                UiElementNode node = (UiElementNode) cookie;
                                String fileName = getFileName(node);
                                File file = new File(imageDir, fileName);
                                if (file.exists()) {
                                    // On Windows, perhaps we need to rename instead?
                                    file.delete();
                                }
                                int x1 = parentX + info.getLeft();
                                int y1 = parentY + info.getTop();
                                int x2 = parentX + info.getRight();
                                int y2 = parentY + info.getBottom();
                                if (x1 != x2 && y1 != y2) {
                                    savePreview(file, image, x1, y1, x2, y2);
                                }
                            }
                        }
                    }
                }

                session.dispose();
            }
        }

        return true;
    }

    /**
     * Look up the background and foreground colors from the theme. May not find either
     * the background or foreground or both, but will always return a pair of possibly
     * null colors.
     *
     * @return a pair of possibly null color descriptions
     */
    private Pair<RGB, RGB> getColorsFromTheme() {
        RGB background = null;
        RGB foreground = null;

        ResourceResolver resources = mPalette.getEditor().createResolver();
        StyleResourceValue theme = resources.getCurrentTheme();
        if (theme != null) {
            background = resolveThemeColor(resources, "windowBackground"); //$NON-NLS-1$
            if (background == null) {
                background = renderDrawableResource("windowBackground"); //$NON-NLS-1$
                // This causes some harm with some themes: We'll find a color, say black,
                // that isn't actually rendered in the theme. Better to use null here,
                // which will cause the caller to pick a pixel from the observed background
                // instead.
                //if (background == null) {
                //    background = resolveThemeColor(resources, "colorBackground"); //$NON-NLS-1$
                //}
            }
            foreground = resolveThemeColor(resources, "textColorPrimary"); //$NON-NLS-1$
        }

        // Ensure that the foreground color is suitably distinct from the background color
        if (background != null) {
            int bgRgb = ImageUtils.rgbToInt(background, 0xFF);
            int backgroundBrightness = ImageUtils.getBrightness(bgRgb);
            if (foreground == null) {
                if (backgroundBrightness < 128) {
                    foreground = new RGB(255, 255, 255);
                } else {
                    foreground = new RGB(0, 0, 0);
                }
            } else {
                int fgRgb = ImageUtils.rgbToInt(foreground, 0xFF);
                int foregroundBrightness = ImageUtils.getBrightness(fgRgb);
                if (Math.abs(backgroundBrightness - foregroundBrightness) < 64) {
                    if (backgroundBrightness < 128) {
                        foreground = new RGB(255, 255, 255);
                    } else {
                        foreground = new RGB(0, 0, 0);
                    }
                }
            }
        }

        return Pair.of(background, foreground);
    }

    /**
     * Renders the given resource which should refer to a drawable and returns a
     * representative color value for the drawable (such as the color in the center)
     *
     * @param themeItemName the item in the theme to be looked up and rendered
     * @return a color representing a typical color in the drawable
     */
    private RGB renderDrawableResource(String themeItemName) {
        GraphicalEditorPart editor = mPalette.getEditor();
        BufferedImage image = editor.renderThemeItem(themeItemName, 100, 100);
        if (image != null) {
            // Use the middle pixel as the color since that works better for gradients;
            // solid colors work too.
            int rgb = image.getRGB(image.getWidth() / 2, image.getHeight() / 2);
            return ImageUtils.intToRgb(rgb);
        }

        return null;
    }

    private static RGB resolveThemeColor(ResourceResolver resources, String resourceName) {
        ResourceValue textColor = resources.findItemInTheme(resourceName);
        textColor = resources.resolveResValue(textColor);
        if (textColor == null) {
            return null;
        }
        String value = textColor.getValue();

        while (value != null) {
            if (value.startsWith("#")) { //$NON-NLS-1$
                try {
                    int rgba = ImageUtils.getColor(value);
                    // Drop alpha channel
                    return ImageUtils.intToRgb(rgba);
                } catch (NumberFormatException nfe) {
                    ;
                }
                return null;
            }
            if (value.startsWith("@")) { //$NON-NLS-1$
                boolean isFramework = textColor.isFramework();
                textColor = resources.findResValue(value, isFramework);
                if (textColor != null) {
                    value = textColor.getValue();
                } else {
                    break;
                }
            } else {
                File file = new File(value);
                if (file.exists() && file.getName().endsWith(DOT_XML)) {
                    // Parse
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    BufferedInputStream bis = null;
                    try {
                        bis = new BufferedInputStream(new FileInputStream(file));
                        InputSource is = new InputSource(bis);
                        factory.setNamespaceAware(true);
                        factory.setValidating(false);
                        DocumentBuilder builder = factory.newDocumentBuilder();
                        Document document = builder.parse(is);
                        NodeList items = document.getElementsByTagName(TAG_ITEM);

                        value = findColorValue(items);
                        continue;
                    } catch (Exception e) {
                        AdtPlugin.log(e, "Failed parsing color file %1$s", file.getName());
                    } finally {
                        if (bis != null) {
                            try {
                                bis.close();
                            } catch (IOException e) {
                                // Nothing useful can be done here
                            }
                        }
                    }
                }

                return null;
            }
        }

        return null;
    }

    /**
     *  Searches a color XML file for the color definition element that does not
     * have an associated state and returns its color
     */
    private static String findColorValue(NodeList items) {
        for (int i = 0, n = items.getLength(); i < n; i++) {
            // Find non-state color definition
            Node item = items.item(i);
            boolean hasState = false;
            if (item.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) item;
                if (element.hasAttributeNS(ANDROID_URI, ATTR_COLOR)) {
                    NamedNodeMap attributes = element.getAttributes();
                    for (int j = 0, m = attributes.getLength(); j < m; j++) {
                        Attr attribute = (Attr) attributes.item(j);
                        if (attribute.getLocalName().startsWith("state_")) { //$NON-NLS-1$
                            hasState = true;
                            break;
                        }
                    }

                    if (!hasState) {
                        return element.getAttributeNS(ANDROID_URI, ATTR_COLOR);
                    }
                }
            }
        }

        return null;
    }

    private String getFileName(ElementDescriptor descriptor) {
        return descriptor.getUiName() + DOT_PNG;
    }

    private String getFileName(UiElementNode node) {
        ViewMetadataRepository repository = ViewMetadataRepository.get();
        String fqn = repository.getFullClassName((Element) node.getXmlNode());
        return fqn.substring(fqn.lastIndexOf('.') + 1) + DOT_PNG;
    }

    /**
     * Cleans up a name by removing punctuation and whitespace etc to make
     * it a better filename
     * @param name
     * @return
     */
    private static String cleanup(String name) {
        // Extract just the characters (no whitespace, parentheses, punctuation etc)
        // to ensure that the filename is pretty portable
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isJavaIdentifierPart(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }

        return sb.toString();
    }

    /** Returns the location of a directory containing image previews (which may not exist) */
    private File getImageDir(boolean create) {
        if (mImageDir == null) {
            // Location for plugin-related state data
            IPath pluginState = AdtPlugin.getDefault().getStateLocation();

            // We have multiple directories - one for each combination of SDK, theme and device
            // (and later, possibly other qualifiers).
            // These are created -lazily-.
            String targetName = mPalette.getCurrentTarget().getFullName();
            String androidTargetNamePrefix = "Android ";
            String themeNamePrefix = "Theme.";
            if (targetName.startsWith(androidTargetNamePrefix)) {
                targetName = targetName.substring(androidTargetNamePrefix.length());
            }
            String themeName = mPalette.getCurrentTheme();
            if (themeName == null) {
                themeName = "Theme"; //$NON-NLS-1$
            }
            if (themeName.startsWith(themeNamePrefix)) {
                themeName = themeName.substring(themeNamePrefix.length());
            }
            String dirName = String.format("palette-preview-r10-%s-%s-%s", cleanup(targetName),
                    cleanup(themeName), cleanup(mPalette.getCurrentDevice()));
            IPath dirPath = pluginState.append(dirName);

            mImageDir = new File(dirPath.toOSString());
        }

        if (create && !mImageDir.exists()) {
            mImageDir.mkdirs();
        }

        return mImageDir;
    }

    private void savePreview(File output, BufferedImage image,
            int left, int top, int right, int bottom) {
        try {
            BufferedImage im = ImageUtils.subImage(image, left, top, right, bottom);
            ImageIO.write(im, "PNG", output); //$NON-NLS-1$
        } catch (IOException e) {
            AdtPlugin.log(e, "Failed writing palette file");
        }
    }

    private void storeBackground(File imageDir, RGB bg, RGB fg) {
        mBackground = bg;
        mForeground = fg;
        File file = new File(imageDir, PREVIEW_INFO_FILE);
        String colors = String.format(
                "background=#%02x%02x%02x\nforeground=#%02x%02x%02x\n", //$NON-NLS-1$
                bg.red, bg.green, bg.blue,
                fg.red, fg.green, fg.blue);
        AdtPlugin.writeFile(file, colors);
    }

    public RGB getBackgroundColor() {
        if (mBackground == null) {
            initColors();
        }

        return mBackground;
    }

    public RGB getForegroundColor() {
        if (mForeground == null) {
            initColors();
        }

        return mForeground;
    }

    public void initColors() {
        try {
            // Already initialized? Foreground can be null which would call
            // initColors again and again, but background is never null after
            // initialization so we use it as the have-initialized flag.
            if (mBackground != null) {
                return;
            }

            File imageDir = getImageDir(false);
            if (!imageDir.exists()) {
                render();

                // Initialized as part of the render
                if (mBackground != null) {
                    return;
                }
            }

            File file = new File(imageDir, PREVIEW_INFO_FILE);
            if (file.exists()) {
                Properties properties = new Properties();
                InputStream is = null;
                try {
                    is = new BufferedInputStream(new FileInputStream(file));;
                    properties.load(is);
                } catch (IOException e) {
                    AdtPlugin.log(e, "Can't read preview properties");
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (IOException e) {
                            // Nothing useful can be done.
                        }
                    }
                }

                String colorString = (String) properties.get("background"); //$NON-NLS-1$
                if (colorString != null) {
                    int rgb = ImageUtils.getColor(colorString.trim());
                    mBackground = ImageUtils.intToRgb(rgb);
                }
                colorString = (String) properties.get("foreground"); //$NON-NLS-1$
                if (colorString != null) {
                    int rgb = ImageUtils.getColor(colorString.trim());
                    mForeground = ImageUtils.intToRgb(rgb);
                }
            }

            if (mBackground == null) {
                mBackground = new RGB(0, 0, 0);
            }
            // mForeground is allowed to be null.
        } catch (Throwable t) {
            AdtPlugin.log(t, "Cannot initialize preview color settings");
        }
    }
}
