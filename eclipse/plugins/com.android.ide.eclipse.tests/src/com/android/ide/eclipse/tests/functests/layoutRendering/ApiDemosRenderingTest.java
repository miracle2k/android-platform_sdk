/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License"); you
 * may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.ide.eclipse.tests.functests.layoutRendering;

import com.android.ide.common.rendering.LayoutLibrary;
import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.rendering.api.IProjectCallback;
import com.android.ide.common.rendering.api.RenderParams;
import com.android.ide.common.rendering.api.RenderSession;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.RenderParams.RenderingMode;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.sdk.LoadStatus;
import com.android.ide.eclipse.adt.internal.resources.configurations.FolderConfiguration;
import com.android.ide.eclipse.adt.internal.resources.configurations.KeyboardStateQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.NavigationMethodQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.PixelDensityQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.ScreenDimensionQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.ScreenOrientationQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.ScreenRatioQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.ScreenSizeQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.TextInputMethodQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.TouchScreenQualifier;
import com.android.ide.eclipse.adt.internal.resources.manager.ProjectResources;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceManager;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData;
import com.android.ide.eclipse.tests.SdkTestCase;
import com.android.resources.Density;
import com.android.resources.Keyboard;
import com.android.resources.KeyboardState;
import com.android.resources.Navigation;
import com.android.resources.ResourceType;
import com.android.resources.ScreenOrientation;
import com.android.resources.ScreenRatio;
import com.android.resources.ScreenSize;
import com.android.resources.TouchScreen;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.io.FolderWrapper;
import com.android.util.Pair;

import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

public class ApiDemosRenderingTest extends SdkTestCase {

    /**
     * Custom parser that implements {@link ILayoutPullParser} (which itself extends
     * {@link XmlPullParser}).
     */
    private final static class TestParser extends KXmlParser implements ILayoutPullParser {
        /**
         * Since we're not going to go through the result of the rendering/layout, we can return
         * null for the View Key.
         */
        public Object getViewCookie() {
            return null;
        }

        public ILayoutPullParser getParser(String layoutName) {
            return null;
        }
    }

    private final static class ProjectCallBack implements IProjectCallback {
        // resource id counter.
        // We start at 0x7f000000 to avoid colliding with the framework id
        // since we have no access to the project R.java and we need to generate them automatically.
        private int mIdCounter = 0x7f000000;

        // in some cases, the id that getResourceValue(String type, String name) returns
        // will be sent back to get the type/name. This map stores the id/type/name we generate
        // to be able to do the reverse resolution.
        private Map<Integer, Pair<ResourceType, String>> mResourceMap =
            new HashMap<Integer, Pair<ResourceType, String>>();

        private boolean mCustomViewAttempt = false;

        public String getNamespace() {
            // TODO: read from the ApiDemos manifest.
            return "com.example.android.apis";
        }

        @SuppressWarnings("unchecked")
        public Object loadView(String name, Class[] constructorSignature, Object[] constructorArgs)
                throws ClassNotFoundException, Exception {
            mCustomViewAttempt = true;
            return null;
        }

        public Integer getResourceId(ResourceType type, String name) {
            Integer result = ++mIdCounter;
            mResourceMap.put(result, Pair.of(type, name));
            return result;
        }

        public Pair<ResourceType, String> resolveResourceId(int id) {
            return mResourceMap.get(id);
        }

        public String resolveResourceId(int[] id) {
            return null;
        }

    }

    public void testApiDemos() throws IOException, XmlPullParserException {
        findApiDemos();
    }

    private void findApiDemos() throws IOException, XmlPullParserException {
        IAndroidTarget[] targets = getSdk().getTargets();

        for (IAndroidTarget target : targets) {
            String path = target.getPath(IAndroidTarget.SAMPLES);
            File samples = new File(path);
            if (samples.isDirectory()) {
                File[] files = samples.listFiles();
                for (File file : files) {
                    if ("apidemos".equalsIgnoreCase(file.getName())) {
                        testSample(target, file);
                        return;
                    }
                }
            }
        }

        fail("Failed to find ApiDemos!");
    }

    private void testSample(IAndroidTarget target, File sampleProject) throws IOException, XmlPullParserException {
        AndroidTargetData data = getSdk().getTargetData(target);
        if (data == null) {
            fail("No AndroidData!");
        }

        LayoutLibrary layoutLib = data.getLayoutLibrary();
        if (layoutLib.getStatus() != LoadStatus.LOADED) {
            fail("Fail to load the bridge: " + layoutLib.getLoadMessage());
        }

        FolderWrapper resFolder = new FolderWrapper(sampleProject, SdkConstants.FD_RES);
        if (resFolder.exists() == false) {
            fail("Sample project has no res folder!");
        }

        // look for the layout folder
        File layoutFolder = new File(resFolder, SdkConstants.FD_LAYOUT);
        if (layoutFolder.isDirectory() == false) {
            fail("Sample project has no layout folder!");
        }

        // first load the project's target framework resource
        ProjectResources framework = ResourceManager.getInstance().loadFrameworkResources(target);

        // now load the project resources
        ProjectResources project = new ProjectResources(null /*project*/);
        ResourceManager.getInstance().loadResources(project, resFolder);

        // Create a folder configuration that will be used for the rendering:
        FolderConfiguration config = getConfiguration();

        // get the configured resources
        Map<ResourceType, Map<String, ResourceValue>> configuredFramework =
                framework.getConfiguredResources(config);
        Map<ResourceType, Map<String, ResourceValue>> configuredProject =
                project.getConfiguredResources(config);

        boolean saveFiles = System.getenv("save_file") != null;

        // loop on the layouts and render them
        File[] layouts = layoutFolder.listFiles();
        for (File layout : layouts) {
            // create a parser for the layout file
            TestParser parser = new TestParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
            parser.setInput(new FileReader(layout));

            System.out.println("Rendering " + layout.getName());

            ProjectCallBack projectCallBack = new ProjectCallBack();

            ResourceResolver resolver = ResourceResolver.create(
                    configuredProject, configuredFramework,
                    "Theme", false /*isProjectTheme*/);

            RenderSession session = layoutLib.createSession(new RenderParams(
                    parser,
                    null /*projectKey*/,
                    320,
                    480,
                    RenderingMode.NORMAL,
                    Density.MEDIUM,
                    160, //xdpi
                    160, // ydpi
                    resolver,
                    projectCallBack,
                    1, // minSdkVersion
                    1, // targetSdkVersion
                    null //logger
                    ));

            if (session.getResult().isSuccess() == false) {
                if (projectCallBack.mCustomViewAttempt == false) {
                    System.out.println("FAILED");
                    fail(String.format("Rendering %1$s: %2$s", layout.getName(),
                            session.getResult().getErrorMessage()));
                } else {
                    System.out.println("Ignore custom views for now");
                }
            } else {
                if (saveFiles) {
                    File tmp = File.createTempFile(layout.getName(), ".png");
                    ImageIO.write(session.getImage(), "png", tmp);
                }
                System.out.println("Success!");
            }
        }
    }

    /**
     * Returns a config. This must be a valid config like a device would return. This is to
     * prevent issues where some resources don't exist in all cases and not in the default
     * (for instance only available in hdpi and mdpi but not in default).
     * @return
     */
    private FolderConfiguration getConfiguration() {
        FolderConfiguration config = new FolderConfiguration();

        // this matches an ADP1.
        config.addQualifier(new ScreenSizeQualifier(ScreenSize.NORMAL));
        config.addQualifier(new ScreenRatioQualifier(ScreenRatio.NOTLONG));
        config.addQualifier(new ScreenOrientationQualifier(ScreenOrientation.PORTRAIT));
        config.addQualifier(new PixelDensityQualifier(Density.MEDIUM));
        config.addQualifier(new TouchScreenQualifier(TouchScreen.FINGER));
        config.addQualifier(new KeyboardStateQualifier(KeyboardState.HIDDEN));
        config.addQualifier(new TextInputMethodQualifier(Keyboard.QWERTY));
        config.addQualifier(new NavigationMethodQualifier(Navigation.TRACKBALL));
        config.addQualifier(new ScreenDimensionQualifier(480, 320));

        return config;
    }
}
