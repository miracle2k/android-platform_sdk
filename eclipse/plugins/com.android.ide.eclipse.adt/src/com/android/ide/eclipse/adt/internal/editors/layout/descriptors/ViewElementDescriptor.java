/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.ide.eclipse.adt.internal.editors.layout.descriptors;

import com.android.ide.eclipse.adt.internal.editors.descriptors.AttributeDescriptor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.ElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.uimodel.UiViewElementNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;

/**
 * {@link ViewElementDescriptor} describes the properties expected for a given XML element node
 * representing a class in an XML Layout file.
 * <p/>
 * These descriptors describe Android views XML elements.
 * <p/>
 * The base class {@link ElementDescriptor} has a notion of "children", that is an XML element
 * can produce another set of XML elements. Because of the flat nature of Android's layout
 * XML files all possible views are children of the document and of themselves (that is any
 * view group can contain any other view). This is an implied contract of this class that is
 * enforces at construction by {@link LayoutDescriptors}. Note that by construction any code
 * that deals with the children hierarchy must also deal with potential infinite loops since views
 * <em>will</em> reference themselves (e.g. a ViewGroup can contain a ViewGroup).
 * <p/>
 * Since Views are also Java classes, they derive from each other. Here this is represented
 * as the "super class", which denotes the fact that a given View java class derives from
 * another class. These properties are also set at construction by {@link LayoutDescriptors}.
 * The super class hierarchy is very different from the descriptor's children hierarchy: the
 * later represents Java inheritance, the former represents an XML nesting capability.
 *
 * @see ElementDescriptor
 */
public final class ViewElementDescriptor extends ElementDescriptor {

    /** The full class name (FQCN) of this view. */
    private String mFullClassName;

    /** The list of layout attributes. Can be empty but not null. */
    private AttributeDescriptor[] mLayoutAttributes;

    /** The super-class descriptor. Can be null. */
    private ViewElementDescriptor mSuperClassDesc;

    /**
     * Constructs a new {@link ViewElementDescriptor} based on its XML name, UI name,
     * the canonical name of the class it represents, its tooltip, its SDK url, its attributes list,
     * its children list and its mandatory flag.
     *
     * @param xml_name The XML element node name. Case sensitive.
     * @param ui_name The XML element name for the user interface, typically capitalized.
     * @param fullClassName The fully qualified class name the {@link ViewElementDescriptor} is
     *          representing.
     * @param tooltip An optional tooltip. Can be null or empty.
     * @param sdk_url An optional SKD URL. Can be null or empty.
     * @param attributes The list of allowed attributes. Can be null or empty.
     * @param layoutAttributes The list of layout attributes. Can be null or empty.
     * @param children The list of allowed children. Can be null or empty.
     * @param mandatory Whether this node must always exist (even for empty models). A mandatory
     *  UI node is never deleted and it may lack an actual XML node attached. A non-mandatory
     *  UI node MUST have an XML node attached and it will cease to exist when the XML node
     *  ceases to exist.
     */
    public ViewElementDescriptor(String xml_name, String ui_name,
            String fullClassName,
            String tooltip, String sdk_url,
            AttributeDescriptor[] attributes, AttributeDescriptor[] layoutAttributes,
            ElementDescriptor[] children, boolean mandatory) {
        super(xml_name, ui_name, tooltip, sdk_url, attributes, children, mandatory);
        mFullClassName = fullClassName;
        mLayoutAttributes = layoutAttributes != null ? layoutAttributes : new AttributeDescriptor[0];
    }

    /**
     * Constructs a new {@link ElementDescriptor} based on its XML name, the canonical
     * name of the class it represents, and its children list.
     * The UI name is build by capitalizing the XML name.
     * The UI nodes will be non-mandatory.
     *
     * @param xml_name The XML element node name. Case sensitive.
     * @param fullClassName The fully qualified class name the {@link ViewElementDescriptor} is
     *          representing.
     * @param children The list of allowed children. Can be null or empty.
     * @param mandatory Whether this node must always exist (even for empty models). A mandatory
     *  UI node is never deleted and it may lack an actual XML node attached. A non-mandatory
     *  UI node MUST have an XML node attached and it will cease to exist when the XML node
     *  ceases to exist.
     *
     *  @deprecated Never used. We should clean it up someday.
     */
    public ViewElementDescriptor(String xml_name, String fullClassName,
            ElementDescriptor[] children,
            boolean mandatory) {
        super(xml_name, children, mandatory);
        mFullClassName = fullClassName;
    }

    /**
     * Constructs a new {@link ElementDescriptor} based on its XML name and children list.
     * The UI name is build by capitalizing the XML name.
     * The UI nodes will be non-mandatory.
     *
     * @param xml_name The XML element node name. Case sensitive.
     * @param fullClassName The fully qualified class name the {@link ViewElementDescriptor} is
     * representing.
     * @param children The list of allowed children. Can be null or empty.
     *
     *  @deprecated Never used. We should clean it up someday.
     */
    public ViewElementDescriptor(String xml_name, String fullClassName,
            ElementDescriptor[] children) {
        super(xml_name, children);
        mFullClassName = fullClassName;
    }

    /**
     * Constructs a new {@link ElementDescriptor} based on its XML name and on the canonical
     * name of the class it represents.
     * The UI name is build by capitalizing the XML name.
     * The UI nodes will be non-mandatory.
     *
     * @param xml_name The XML element node name. Case sensitive.
     * @param fullClassName The fully qualified class name the {@link ViewElementDescriptor} is
     * representing.
     */
    public ViewElementDescriptor(String xml_name, String fullClassName) {
        super(xml_name);
        mFullClassName = fullClassName;
    }

    /**
     * Returns the fully qualified name of the View class represented by this element descriptor
     * e.g. "android.view.View".
     */
    public String getFullClassName() {
        return mFullClassName;
    }

    /** Returns the list of layout attributes. Can be empty but not null. */
    public AttributeDescriptor[] getLayoutAttributes() {
        return mLayoutAttributes;
    }

    /**
     * Returns a new {@link UiViewElementNode} linked to this descriptor.
     */
    @Override
    public UiElementNode createUiNode() {
        return new UiViewElementNode(this);
    }

    /**
     * Returns the {@link ViewElementDescriptor} of the super-class of this View descriptor
     * that matches the java View hierarchy. Can be null.
     */
    public ViewElementDescriptor getSuperClassDesc() {
        return mSuperClassDesc;
    }

    /**
     * Sets the {@link ViewElementDescriptor} of the super-class of this View descriptor
     * that matches the java View hierarchy. Can be null.
     */
    public void setSuperClass(ViewElementDescriptor superClassDesc) {
        mSuperClassDesc = superClassDesc;
    }
}
