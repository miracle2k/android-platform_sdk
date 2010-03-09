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

package com.android.adt.gscripts;

/**
 * An {@link IViewRule} for android.widget.FrameLayout and all its derived classes.
 */
public class AndroidWidgetFrameLayoutRule extends BaseLayout {

    // ==== Drag'n'drop support ====
    // The FrameLayout accepts any drag'n'drop anywhere on its surface.

    DropFeedback onDropEnter(INode targetNode, String fqcn) {
        return new DropFeedback(
            [ "p": null ],      // Point: last cursor position
            {
                gc, node, feedback ->
                // Paint closure for the FrameLayout.

                Rect b = node.getBounds();
                if (!b.isValid()) {
                    return;
                }

                gc.setForeground(gc.registerColor(0x00FFFF00));
                gc.setLineStyle(IGraphics.LineStyle.LINE_SOLID);
                gc.setLineWidth(2);
                gc.drawRect(b);

                Point p = feedback.userData.p;
                if (p != null) {
                    int x = p.x;
                    int y = p.y;
                    gc.drawLine(x - 10, y - 10, x + 10, y + 10);
                    gc.drawLine(x + 10, y - 10, x - 10, y + 10);
                    gc.drawRect(x - 10, y - 10, x + 10, y + 10);
                }
            });
    }

    DropFeedback onDropMove(INode targetNode, String fqcn, DropFeedback feedback, Point p) {
        feedback.userData.p = p;
        feedback.requestPaint = true;
        return feedback;
    }

    void onDropLeave(INode targetNode, String fqcn, DropFeedback feedback) {
        // ignore
    }

    void onDropped(INode targetNode, String fqcn, DropFeedback feedback, Point p) {

        // Get the last component of the FQCN (e.g. "android.view.Button" => "Button")
        String name = fqcn;
        name = name[name.lastIndexOf(".")+1 .. name.length()-1];

        targetNode.editXml("Add ${name} to FrameLayout") {
            targetNode.appendChild(fqcn);
        }
    }
}
