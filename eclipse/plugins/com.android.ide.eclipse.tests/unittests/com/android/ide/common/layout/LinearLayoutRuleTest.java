/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.ide.common.layout;

import com.android.ide.common.api.DropFeedback;
import com.android.ide.common.api.IDragElement;
import com.android.ide.common.api.IMenuCallback;
import com.android.ide.common.api.INode;
import com.android.ide.common.api.IViewRule;
import com.android.ide.common.api.MenuAction;
import com.android.ide.common.api.Point;
import com.android.ide.common.api.Rect;
import com.android.ide.common.api.MenuAction.Choices;

import java.util.List;

/** Test the {@link LinearLayoutRule} */
public class LinearLayoutRuleTest extends AbstractLayoutRuleTest {
    // Utility for other tests
    protected void dragIntoEmpty(Rect dragBounds) {
        boolean haveBounds = dragBounds.isValid();

        IViewRule rule = new LinearLayoutRule();
        INode targetNode = TestNode.create("android.widget.LinearLayout").id(
        "@+id/LinearLayout01").bounds(new Rect(0, 0, 240, 480));
        Point dropPoint = new Point(10, 5);

        IDragElement[] elements = TestDragElement.create(TestDragElement.create(
                "android.widget.Button", dragBounds).id("@+id/Button01"));

        // Enter target
        DropFeedback feedback = rule.onDropEnter(targetNode, elements);
        assertNotNull(feedback);
        assertFalse(feedback.invalidTarget);
        assertNotNull(feedback.painter);

        feedback = rule.onDropMove(targetNode, elements, feedback, dropPoint);
        assertNotNull(feedback);
        assertFalse(feedback.invalidTarget);

        // Paint feedback and make sure it's what we expect
        TestGraphics graphics = new TestGraphics();
        assertNotNull(feedback.painter);
        feedback.painter.paint(graphics, targetNode, feedback);
        assertEquals(
                // Expect to see a recipient rectangle around the bounds of the
                // LinearLayout,
                // as well as a single vertical line as a drop preview located
                // along the left
                // edge (for this horizontal linear layout) showing insert
                // position at index 0,
                // and finally a rectangle for the bounds of the inserted button
                // centered over
                // the middle
                "[useStyle(DROP_RECIPIENT), "
                        +
                        // Bounds rectangle
                        "drawRect(Rect[0,0,240,480]), "
                        + "useStyle(DROP_ZONE), drawLine(1,0,1,480), "
                        + "useStyle(DROP_ZONE_ACTIVE), " + "useStyle(DROP_PREVIEW), " +
                        // Insert position line
                        "drawLine(1,0,1,480)" + (haveBounds ?
                        // Outline of dragged node centered over position line
                        ", useStyle(DROP_PREVIEW), " + "drawRect(Rect[1,0,100,80])"
                                // Nothing when we don't have bounds
                                : "") + "]", graphics.getDrawn().toString());

        // Attempt a drop
        assertEquals(0, targetNode.getChildren().length);
        rule.onDropped(targetNode, elements, feedback, dropPoint);
        assertEquals(1, targetNode.getChildren().length);
        assertEquals("@+id/Button01", targetNode.getChildren()[0].getStringAttr(
                BaseView.ANDROID_URI, BaseView.ATTR_ID));
    }

    // Utility for other tests
    protected INode dragInto(boolean vertical, Rect dragBounds, Point dragPoint,
            int insertIndex, int currentIndex,
            String... graphicsFragments) {
        INode linearLayout = TestNode.create("android.widget.LinearLayout").id(
                "@+id/LinearLayout01").bounds(new Rect(0, 0, 240, 480)).set(BaseView.ANDROID_URI,
                LinearLayoutRule.ATTR_ORIENTATION,
                vertical ? LinearLayoutRule.VALUE_VERTICAL : LinearLayoutRule.VALUE_HORIZONTAL)
                .add(
                        TestNode.create("android.widget.Button").id("@+id/Button01").bounds(
                                new Rect(0, 0, 100, 80)),
                        TestNode.create("android.widget.Button").id("@+id/Button02").bounds(
                                new Rect(0, 100, 100, 80)),
                        TestNode.create("android.widget.Button").id("@+id/Button03").bounds(
                                new Rect(0, 200, 100, 80)),
                        TestNode.create("android.widget.Button").id("@+id/Button04").bounds(
                                new Rect(0, 300, 100, 80)));

        return super.dragInto(new LinearLayoutRule(), linearLayout, dragBounds, dragPoint, null,
                insertIndex, currentIndex, graphicsFragments);
    }

    // Check that the context menu registers the expected menu items
    public void testContextMenu() {
        LinearLayoutRule rule = new LinearLayoutRule();
        INode node = TestNode.create("android.widget.Button").id("@+id/Button012");

        List<MenuAction> contextMenu = rule.getContextMenu(node);
        assertEquals(4, contextMenu.size());
        assertEquals("Layout Width", contextMenu.get(0).getTitle());
        assertEquals("Layout Height", contextMenu.get(1).getTitle());
        assertEquals("Properties", contextMenu.get(2).getTitle());
        assertEquals("Orientation", contextMenu.get(3).getTitle());

        MenuAction propertiesMenu = contextMenu.get(2);
        assertTrue(propertiesMenu.getClass().getName(), propertiesMenu instanceof MenuAction.Group);
        // TODO: Test Properties-list
    }

    // Check that the context menu manipulates the orientation attribute
    public void testOrientation() {
        LinearLayoutRule rule = new LinearLayoutRule();
        INode node = TestNode.create("android.widget.Button").id("@+id/Button012");

        assertNull(node.getStringAttr(BaseView.ANDROID_URI, LinearLayoutRule.ATTR_ORIENTATION));

        List<MenuAction> contextMenu = rule.getContextMenu(node);
        assertEquals(4, contextMenu.size());
        MenuAction orientationAction = contextMenu.get(3);

        assertTrue(orientationAction.getClass().getName(),
                orientationAction instanceof MenuAction.Choices);

        MenuAction.Choices choices = (Choices) orientationAction;
        IMenuCallback callback = choices.getCallback();
        callback.action(orientationAction, LinearLayoutRule.VALUE_VERTICAL, true);

        String orientation = node.getStringAttr(BaseView.ANDROID_URI,
                LinearLayoutRule.ATTR_ORIENTATION);
        assertEquals(LinearLayoutRule.VALUE_VERTICAL, orientation);
        callback.action(orientationAction, LinearLayoutRule.VALUE_HORIZONTAL, true);
        orientation = node.getStringAttr(BaseView.ANDROID_URI, LinearLayoutRule.ATTR_ORIENTATION);
        assertEquals(LinearLayoutRule.VALUE_HORIZONTAL, orientation);
    }

    public void testDragInEmptyWithBounds() {
        dragIntoEmpty(new Rect(0, 0, 100, 80));
    }

    public void testDragInEmptyWithoutBounds() {
        dragIntoEmpty(new Rect(0, 0, 0, 0));
    }

    public void testDragInVerticalTop() {
        dragInto(true,
                // Bounds of the dragged item
                new Rect(0, 0, 105, 80),
                // Drag point
                new Point(30, -10),
                // Expected insert location
                0,
                // Not dragging one of the existing children
                -1,
                // Bounds rectangle
                "useStyle(DROP_RECIPIENT), drawRect(Rect[0,0,240,480])",

                // Drop zones
                "useStyle(DROP_ZONE), drawLine(0,0,240,0), drawLine(0,90,240,90), "
                        + "drawLine(0,190,240,190), drawLine(0,290,240,290), "
                        + "drawLine(0,381,240,381)",

                // Active nearest line
                "useStyle(DROP_ZONE_ACTIVE), useStyle(DROP_PREVIEW), drawLine(0,0,240,0)",

                // Preview of the dropped rectangle
                "useStyle(DROP_PREVIEW), drawRect(Rect[0,-40,105,80])");

        // Without drag bounds it should be identical except no preview
        // rectangle
        dragInto(true,
                new Rect(0, 0, 0, 0), // Invalid
                new Point(30, -10), 0, -1,
                "useStyle(DROP_ZONE_ACTIVE), useStyle(DROP_PREVIEW), drawLine(0,0,240,0)");
    }

    public void testDragInVerticalBottom() {
        dragInto(true,
                // Bounds of the dragged item
                new Rect(0, 0, 105, 80),
                // Drag point
                new Point(30, 500),
                // Expected insert location
                4,
                // Not dragging one of the existing children
                -1,
                // Bounds rectangle
                "useStyle(DROP_RECIPIENT), drawRect(Rect[0,0,240,480])",

                // Drop zones
                "useStyle(DROP_ZONE), drawLine(0,0,240,0), drawLine(0,90,240,90), "
                        + "drawLine(0,190,240,190), drawLine(0,290,240,290), drawLine(0,381,240,381), ",

                // Active nearest line
                "useStyle(DROP_ZONE_ACTIVE), useStyle(DROP_PREVIEW), drawLine(0,381,240,381)",

                // Preview of the dropped rectangle
                "useStyle(DROP_PREVIEW), drawRect(Rect[0,381,105,80])");

        // Check without bounds too
        dragInto(true, new Rect(0, 0, 105, 80), new Point(30, 500), 4, -1,
                "useStyle(DROP_PREVIEW), drawRect(Rect[0,381,105,80])");
    }

    public void testDragInVerticalMiddle() {
        dragInto(true,
                // Bounds of the dragged item
                new Rect(0, 0, 105, 80),
                // Drag point
                new Point(0, 170),
                // Expected insert location
                2,
                // Not dragging one of the existing children
                -1,
                // Bounds rectangle
                "useStyle(DROP_RECIPIENT), drawRect(Rect[0,0,240,480])",

                // Drop zones
                "useStyle(DROP_ZONE), drawLine(0,0,240,0), drawLine(0,90,240,90), "
                        + "drawLine(0,190,240,190), drawLine(0,290,240,290)",

                // Active nearest line
                "useStyle(DROP_ZONE_ACTIVE), useStyle(DROP_PREVIEW), drawLine(0,190,240,190)",

                // Preview of the dropped rectangle
                "useStyle(DROP_PREVIEW), drawRect(Rect[0,150,105,80])");

        // Check without bounds too
        dragInto(true, new Rect(0, 0, 105, 80), new Point(0, 170), 2, -1,
                "useStyle(DROP_PREVIEW), drawRect(Rect[0,150,105,80])");
    }

    public void testDragInVerticalMiddleSelfPos() {
        // Drag the 2nd button, down to the position between 3rd and 4th
        dragInto(true,
                // Bounds of the dragged item
                new Rect(0, 100, 100, 80),
                // Drag point
                new Point(0, 250),
                // Expected insert location
                2,
                // Dragging 1st item
                1,
                // Bounds rectangle

                "useStyle(DROP_RECIPIENT), drawRect(Rect[0,0,240,480])",

                // Drop zones - these are different because we exclude drop
                // zones around the
                // dragged item itself (it doesn't make sense to insert directly
                // before or after
                // myself
                "useStyle(DROP_ZONE), drawLine(0,0,240,0), drawLine(0,290,240,290), "
                        + "drawLine(0,381,240,381)",

                // Preview line along insert axis
                "useStyle(DROP_ZONE_ACTIVE), useStyle(DROP_PREVIEW), drawLine(0,290,240,290)",

                // Preview of dropped rectangle
                "useStyle(DROP_PREVIEW), drawRect(Rect[0,250,100,80])");

        // Test dropping on self (no position change):
        dragInto(true,
                // Bounds of the dragged item
                new Rect(0, 100, 100, 80),
                // Drag point
                new Point(0, 210),
                // Expected insert location
                1,
                // Dragging from same pos
                1,
                // Bounds rectangle
                "useStyle(DROP_RECIPIENT), drawRect(Rect[0,0,240,480])",

                // Drop zones - these are different because we exclude drop
                // zones around the
                // dragged item itself (it doesn't make sense to insert directly
                // before or after
                // myself
                "useStyle(DROP_ZONE), drawLine(0,0,240,0), drawLine(0,290,240,290), "
                        + "drawLine(0,381,240,381)",

                // No active nearest line when you're over the self pos!

                // Preview of the dropped rectangle
                "useStyle(DROP_ZONE_ACTIVE), useStyle(DROP_PREVIEW), drawRect(Rect[0,100,100,80])");
    }

    public void testDragToLastPosition() {
        // Drag a button to the last position -- and confirm that the preview rectangle
        // is now shown midway between the second to last and last positions, but fully
        // below the drop zone line:
        dragInto(true,
                // Bounds of the dragged item
                new Rect(0, 100, 100, 80),
                // Drag point
                new Point(0, 400),
                // Expected insert location
                3,
                // Dragging 1st item
                1,

                // Bounds rectangle
                "useStyle(DROP_RECIPIENT), drawRect(Rect[0,0,240,480])",

                // Drop Zones
                "useStyle(DROP_ZONE), drawLine(0,0,240,0), drawLine(0,290,240,290), " +
                "drawLine(0,381,240,381), ",

                // Active Drop Zone
                "useStyle(DROP_ZONE_ACTIVE), useStyle(DROP_PREVIEW), drawLine(0,381,240,381)",

                // Drop Preview
                "seStyle(DROP_PREVIEW), drawRect(Rect[0,381,100,80])");
    }

    // Left to test:
    // Check inserting at last pos with multiple children
    // Check inserting with no bounds rectangle for dragged element
}
