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
import static com.android.ide.common.layout.LayoutConstants.ATTR_ID;

import com.android.ide.eclipse.adt.internal.editors.descriptors.DescriptorsUtils;
import com.android.util.Pair;

import org.eclipse.jface.text.IDocument;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.sse.core.internal.provisional.IndexedRegion;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.HashSet;
import java.util.Set;

@SuppressWarnings("restriction") // No replacement for restricted XML model yet
public class DomUtilities {
    /**
     * Returns the XML DOM node corresponding to the given offset of the given
     * document.
     *
     * @param document The document to look in
     * @param offset The offset to look up the node for
     * @return The node containing the offset, or null
     */
    public static Node getNode(IDocument document, int offset) {
        Node node = null;
        IModelManager modelManager = StructuredModelManager.getModelManager();
        if (modelManager == null) {
            return null;
        }
        try {
            IStructuredModel model = modelManager.getExistingModelForRead(document);
            if (model != null) {
                try {
                    for (; offset >= 0 && node == null; --offset) {
                        node = (Node) model.getIndexedRegion(offset);
                    }
                } finally {
                    model.releaseFromRead();
                }
            }
        } catch (Exception e) {
            // Ignore exceptions.
        }

        return node;
    }

    /**
     * Like {@link #getNode(IDocument, int)}, but has a bias parameter which lets you
     * indicate whether you want the search to look forwards or backwards.
     * This is vital when trying to compute a node range. Consider the following
     * XML fragment:
     * {@code
     *    <a/><b/>[<c/><d/><e/>]<f/><g/>
     * }
     * Suppose we want to locate the nodes in the range indicated by the brackets above.
     * If we want to search for the node corresponding to the start position, should
     * we pick the node on its left or the node on its right? Similarly for the end
     * position. Clearly, we'll need to bias the search towards the right when looking
     * for the start position, and towards the left when looking for the end position.
     * The following method lets us do just that. When passed an offset which sits
     * on the edge of the computed node, it will pick the neighbor based on whether
     * "forward" is true or false, where forward means searching towards the right
     * and not forward is obviously towards the left.
     * @param document the document to search in
     * @param offset the offset to search for
     * @param forward if true, search forwards, otherwise search backwards when on node boundaries
     * @return the node which surrounds the given offset, or the node adjacent to the offset
     *    where the side depends on the forward parameter
     */
    public static Node getNode(IDocument document, int offset, boolean forward) {
        Node node = getNode(document, offset);

        if (node instanceof IndexedRegion) {
            IndexedRegion region = (IndexedRegion) node;

            if (!forward && offset <= region.getStartOffset()) {
                Node left = node.getPreviousSibling();
                if (left == null) {
                    left = node.getParentNode();
                }

                node = left;
            } else if (forward && offset >= region.getEndOffset()) {
                Node right = node.getNextSibling();
                if (right == null) {
                    right = node.getParentNode();
                }
                node = right;
            }
        }

        return node;
    }

    /**
     * Returns a range of elements for the given caret range. Note that the two elements
     * may not be at the same level so callers may want to perform additional input
     * filtering.
     *
     * @param document the document to search in
     * @param beginOffset the beginning offset of the range
     * @param endOffset the ending offset of the range
     * @return a pair of begin+end elements, or null
     */
    public static Pair<Element, Element> getElementRange(IDocument document, int beginOffset,
            int endOffset) {
        Element beginElement = null;
        Element endElement = null;
        Node beginNode = getNode(document, beginOffset, true);
        Node endNode = beginNode;
        if (endOffset > beginOffset) {
            endNode = getNode(document, endOffset, false);
        }

        if (beginNode == null || endNode == null) {
            return null;
        }

        // Adjust offsets if you're pointing at text
        if (beginNode.getNodeType() != Node.ELEMENT_NODE) {
            // <foo> <bar1/> | <bar2/> </foo> => should pick <bar2/>
            beginElement = getNextElement(beginNode);
            if (beginElement == null) {
                // Might be inside the end of a parent, e.g.
                // <foo> <bar/> | </foo> => should pick <bar/>
                beginElement = getPreviousElement(beginNode);
                if (beginElement == null) {
                    // We must be inside an empty element,
                    // <foo> | </foo>
                    // In that case just pick the parent.
                    beginElement = getParentElement(beginNode);
                }
            }
        } else {
            beginElement = (Element) beginNode;
        }

        if (endNode.getNodeType() != Node.ELEMENT_NODE) {
            // In the following, | marks the caret position:
            // <foo> <bar1/> | <bar2/> </foo> => should pick <bar1/>
            endElement = getPreviousElement(endNode);
            if (endElement == null) {
                // Might be inside the beginning of a parent, e.g.
                // <foo> | <bar/></foo> => should pick <bar/>
                endElement = getNextElement(endNode);
                if (endElement == null) {
                    // We must be inside an empty element,
                    // <foo> | </foo>
                    // In that case just pick the parent.
                    endElement = getParentElement(endNode);
                }
            }
        } else {
            endElement = (Element) endNode;
        }

        if (beginElement != null && endElement != null) {
            return Pair.of(beginElement, endElement);
        }

        return null;
    }

    /**
     * Returns the next sibling element of the node, or null if there is no such element
     *
     * @param node the starting node
     * @return the next sibling element, or null
     */
    public static Element getNextElement(Node node) {
        while (node != null && node.getNodeType() != Node.ELEMENT_NODE) {
            node = node.getNextSibling();
        }

        return (Element) node; // may be null as well
    }

    /**
     * Returns the previous sibling element of the node, or null if there is no such element
     *
     * @param node the starting node
     * @return the previous sibling element, or null
     */
    public static Element getPreviousElement(Node node) {
        while (node != null && node.getNodeType() != Node.ELEMENT_NODE) {
            node = node.getPreviousSibling();
        }

        return (Element) node; // may be null as well
    }

    /**
     * Returns the closest ancestor element, or null if none
     *
     * @param node the starting node
     * @return the closest parent element, or null
     */
    public static Element getParentElement(Node node) {
        while (node != null && node.getNodeType() != Node.ELEMENT_NODE) {
            node = node.getParentNode();
        }

        return (Element) node; // may be null as well
    }

    /**
     * Converts the given attribute value to an XML-attribute-safe value, meaning that
     * single and double quotes are replaced with their corresponding XML entities.
     *
     * @param attrValue the value to be escaped
     * @return the escaped value
     */
    public static String toXmlAttributeValue(String attrValue) {
        // Must escape ' and "
        if (attrValue.indexOf('"') == -1 && attrValue.indexOf('\'') == -1) {
            return attrValue;
        }

        int n = attrValue.length();
        StringBuilder sb = new StringBuilder(2 * n);
        for (int i = 0; i < n; i++) {
            char c = attrValue.charAt(i);
            if (c == '"') {
                sb.append("&quot;"); //$NON-NLS-1$
            } else if (c == '\'') {
                sb.append("&apos;"); //$NON-NLS-1$
            } else {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    /** Utility used by {@link #getFreeWidgetId(Element)} */
    private static void addLowercaseIds(Element root, Set<String> seen) {
        if (root.hasAttributeNS(ANDROID_URI, ATTR_ID)) {
            String id = root.getAttributeNS(ANDROID_URI, ATTR_ID);
            seen.add(id.toLowerCase());
        }
    }

    /**
     * Returns a suitable new widget id (not including the {@code @id/} prefix) for the
     * given element, which is guaranteed to be unique in this document
     *
     * @param element the element to compute a new widget id for
     * @return a unique id, never null, which does not include the {@code @id/} prefix
     * @see DescriptorsUtils#getFreeWidgetId
     */
    public static String getFreeWidgetId(Element element) {
        Set<String> ids = new HashSet<String>();
        addLowercaseIds(element.getOwnerDocument().getDocumentElement(), ids);

        String prefix = element.getTagName();
        String generated;
        int num = 1;
        do {
            num++;
            generated = String.format("%1$s%2$d", prefix, num);   //$NON-NLS-1$
        } while (ids.contains(generated.toLowerCase()));

        return generated;
    }

}
