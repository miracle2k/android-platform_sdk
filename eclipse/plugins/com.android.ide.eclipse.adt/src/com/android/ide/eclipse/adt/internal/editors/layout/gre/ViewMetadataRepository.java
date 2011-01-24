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

package com.android.ide.eclipse.adt.internal.editors.layout.gre;

import static com.android.ide.common.api.IViewMetadata.FillPreference.NONE;
import static com.android.ide.eclipse.adt.internal.editors.layout.descriptors.LayoutDescriptors.VIEW_INCLUDE;
import static com.android.ide.eclipse.adt.internal.editors.layout.descriptors.LayoutDescriptors.VIEW_MERGE;

import com.android.ide.common.api.IViewMetadata.FillPreference;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.layout.descriptors.LayoutDescriptors;
import com.android.ide.eclipse.adt.internal.editors.layout.descriptors.ViewElementDescriptor;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData;
import com.android.sdklib.util.Pair;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * The {@link ViewMetadataRepository} contains additional metadata for Android view
 * classes
 */
public class ViewMetadataRepository {
    /** Singleton instance */
    private static ViewMetadataRepository sInstance = new ViewMetadataRepository();

    /**
     * Returns the singleton instance
     *
     * @return the {@link ViewMetadataRepository}
     */
    public static ViewMetadataRepository get() {
        return sInstance;
    }

    /**
     * Ever increasing counter used to assign natural ordering numbers to views and
     * categories
     */
    private static int sNextOrdinal = 0;

    /**
     * List of categories (which contain views); constructed lazily so use
     * {@link #getCategories()}
     */
    private List<CategoryData> mCategories;

    /**
     * Map from class names to view data objects; constructed lazily so use
     * {@link #getClassToView}
     */
    private Map<String, ViewData> mClassToView;

    /** Hidden constructor: Create via factory {@link #get()} instead */
    private ViewMetadataRepository() {
    }

    /** Returns a map from class fully qualified names to {@link ViewData} objects */
    private Map<String, ViewData> getClassToView() {
        if (mClassToView == null) {
            int initialSize = 75;
            mClassToView = new HashMap<String, ViewData>(initialSize);
            List<CategoryData> categories = getCategories();
            for (CategoryData category : categories) {
                for (ViewData view : category) {
                    mClassToView.put(view.getFcqn(), view);
                }
            }
            assert mClassToView.size() <= initialSize;
        }

        return mClassToView;
    }

    /** Returns an ordered list of categories and views, parsed from a metadata file */
    private List<CategoryData> getCategories() {
        if (mCategories == null) {
            mCategories = new ArrayList<CategoryData>();

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            String fileName = "extra-view-metadata.xml"; //$NON-NLS-1$
            InputStream inputStream = ViewMetadataRepository.class.getResourceAsStream(fileName);
            InputSource is = new InputSource(new BufferedInputStream(inputStream));
            try {
                factory.setNamespaceAware(true);
                factory.setValidating(false);
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document document = builder.parse(is);
                Map<String, FillPreference> fillTypes = new HashMap<String, FillPreference>();
                for (FillPreference pref : FillPreference.values()) {
                    fillTypes.put(pref.toString().toLowerCase(), pref);
                }

                NodeList categoryNodes = document.getDocumentElement().getChildNodes();
                for (int i = 0, n = categoryNodes.getLength(); i < n; i++) {
                    Node node = categoryNodes.item(i);
                    if (node.getNodeType() == Node.ELEMENT_NODE) {
                        Element element = (Element) node;
                        if (element.getNodeName().equals("category")) { //$NON-NLS-1$
                            String name = element.getAttribute("name"); //$NON-NLS-1$
                            CategoryData category = new CategoryData(name);
                            NodeList children = element.getChildNodes();
                            for (int j = 0, m = children.getLength(); j < m; j++) {
                                Node childNode = children.item(j);
                                if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                                    Element child = (Element) childNode;
                                    String fqcn = child.getAttribute("class"); //$NON-NLS-1$
                                    String fill = child.getAttribute("fill"); //$NON-NLS-1$
                                    FillPreference fillPreference = null;
                                    if (fill.length() > 0) {
                                        fillPreference = fillTypes.get(fill);
                                    }
                                    if (fillPreference == null) {
                                        fillPreference = NONE;
                                    }
                                    ViewData view = new ViewData(category, fqcn, fillPreference);
                                    category.addView(view);
                                }
                            }

                            mCategories.add(category);
                        }
                    }
                }
            } catch (Exception e) {
                AdtPlugin.log(e, "Invalid palette metadata"); //$NON-NLS-1$
            }
        }

        return mCategories;
    }

    /**
     * Computes the palette entries for the given {@link AndroidTargetData}, looking up the
     * available node descriptors, categorizing and sorting them.
     *
     * @param targetData the target data for which to compute palette entries
     * @return a list of pairs where each pair contains of the category label and an
     *         ordered list of elements to be included in that category
     */
    public List<Pair<String, List<ViewElementDescriptor>>> getPaletteEntries(
            AndroidTargetData targetData) {
        List<Pair<String, List<ViewElementDescriptor>>> result =
            new ArrayList<Pair<String, List<ViewElementDescriptor>>>();

        final Map<String, ViewData> viewMap = getClassToView();
        Map<CategoryData, List<ViewElementDescriptor>> categories =
            new TreeMap<CategoryData, List<ViewElementDescriptor>>();

        // Locate the "Other" category
        CategoryData other = null;
        for (CategoryData category : getCategories()) {
            if (category.getViewCount() == 0) {
                other = category;
                break;
            }
        }

        List<List<ViewElementDescriptor>> lists = new ArrayList<List<ViewElementDescriptor>>(2);
        LayoutDescriptors layoutDescriptors = targetData.getLayoutDescriptors();
        lists.add(layoutDescriptors.getViewDescriptors());
        lists.add(layoutDescriptors.getLayoutDescriptors());

        for (List<ViewElementDescriptor> list : lists) {
            for (ViewElementDescriptor view : list) {
                String name = view.getXmlLocalName();

                // Exclude the <include> and <merge> tags from the View palette.
                // We don't have drop support for it right now, although someday we should.
                if (VIEW_INCLUDE.equals(name) || VIEW_MERGE.equals(name)) {
                    continue;
                }

                ViewData viewData = getClassToView().get(view.getFullClassName());
                CategoryData category = other;
                if (viewData != null) {
                    category = viewData.getCategory();
                }

                List<ViewElementDescriptor> viewList = categories.get(category);
                if (viewList == null) {
                    viewList = new ArrayList<ViewElementDescriptor>();
                    categories.put(category, viewList);
                }
                viewList.add(view);

            }
        }

        for (Map.Entry<CategoryData, List<ViewElementDescriptor>> entry : categories.entrySet()) {
            String name = entry.getKey().getName();
            List<ViewElementDescriptor> items = entry.getValue();
            if (items == null) {
                continue; // empty category
            }

            // Natural sort of the descriptors
            Comparator<ViewElementDescriptor> comparator = new Comparator<ViewElementDescriptor>() {
                public int compare(ViewElementDescriptor v1, ViewElementDescriptor v2) {
                    String fqcn1 = v1.getFullClassName();
                    String fqcn2 = v2.getFullClassName();
                    if (fqcn1 == null) {
                        // <view> and <merge> tags etc
                        fqcn1 = v1.getUiName();
                    }
                    if (fqcn2 == null) {
                        fqcn2 = v2.getUiName();
                    }
                    ViewData d1 = viewMap.get(fqcn1);
                    ViewData d2 = viewMap.get(fqcn2);

                    // Use natural sorting order of the view data
                    // Sort unknown views to the end (and alphabetically among themselves)
                    if (d1 != null) {
                        if (d2 != null) {
                            return d1.getOrdinal() - d2.getOrdinal();
                        } else {
                            return 1;
                        }
                    } else {
                        if (d2 == null) {
                            return v1.getUiName().compareTo(v2.getUiName());
                        } else {
                            return -1;
                        }
                    }
                }
            };
            Collections.sort(items, comparator);
            result.add(Pair.of(name, items));
        }

        return result;
    }

    /**
     * Metadata holder for a particular category - contains the name of the category, its
     * ordinal (for natural/logical sorting order) and views contained in the category
     */
    private static class CategoryData implements Iterable<ViewData>, Comparable<CategoryData> {
        /** Category name */
        private final String mName;
        /** Views included in this category */
        private final List<ViewData> mViews = new ArrayList<ViewData>();
        /** Natural ordering rank */
        private final int mOrdinal = sNextOrdinal++;

        /** Constructs a new category with the given name */
        private CategoryData(String name) {
            super();
            mName = name;
        }

        /** Adds a new view into this category */
        private void addView(ViewData view) {
            mViews.add(view);
        }

        private String getName() {
            return mName;
        }

        public int getViewCount() {
            return mViews.size();
        }

        // Implements Iterable<ViewData> such that we can use for-each on the category to
        // enumerate its views
        public Iterator<ViewData> iterator() {
            return mViews.iterator();
        }

        // Implements Comparable<CategoryData> such that categories can be naturally sorted
        public int compareTo(CategoryData other) {
            return mOrdinal - other.mOrdinal;
        }
    }

    /** Metadata holder for a view of a given fully qualified class name */
    private static class ViewData implements Comparable<ViewData> {
        /** The fully qualified class name of the view */
        private final String mFqcn;
        /** Fill preference of the view */
        private final FillPreference mFillPreference;
        /** The category that the view belongs to */
        private final CategoryData mCategory;
        /** The relative rank of the view for natural ordering */
        private final int mOrdinal = sNextOrdinal++;

        /** Constructs a new view data for the given class */
        private ViewData(CategoryData category, String fqcn, FillPreference fillPreference) {
            super();
            mCategory = category;
            mFqcn = fqcn;
            mFillPreference = fillPreference;
        }

        /** Returns the category for views of this type */
        private CategoryData getCategory() {
            return mCategory;
        }

        /** Returns the {@link FillPreference} for views of this type */
        private FillPreference getFillPreference() {
            return mFillPreference;
        }

        /** Fully qualified class name of views of this type */
        private String getFcqn() {
            return mFqcn;
        }

        /** Relative rank of this view type */
        private int getOrdinal() {
            return mOrdinal;
        }

        // Implements Comparable<ViewData> such that views can be sorted naturally
        public int compareTo(ViewData other) {
            return mOrdinal - other.mOrdinal;
        }
    }

    /**
     * Returns the {@link FillPreference} for classes with the given fully qualified class
     * name
     *
     * @param fqcn the fully qualified class name of the view
     * @return a suitable {@link FillPreference} for the given view type
     */
    public FillPreference getFillPreference(String fqcn) {
        ViewData view = getClassToView().get(fqcn);
        if (view != null) {
            return view.getFillPreference();
        }

        return FillPreference.NONE;
    }
}
