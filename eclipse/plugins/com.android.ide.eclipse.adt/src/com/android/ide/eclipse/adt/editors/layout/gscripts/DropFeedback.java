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

package com.android.ide.eclipse.adt.editors.layout.gscripts;

import groovy.lang.Closure;

/**
 * Returned by onDropEnter/Move and passed to over onDropXyz methods.
 */
public class DropFeedback {
    /**
     * User data that the rule can use in any way it wants to carry state from one
     * operation to another.
     */
    public Object userData;

    /**
     * If true the next screen update will invoke the paint closure.
     */
    public boolean requestPaint;

    /**
     * Closure invoked by the canvas to paint the feedback.
     * The closure will receive 3 arguments: <br/>
     * - The {@link IGraphics} context to use for painting. Must not be cached. <br/>
     * - The {@link INode} target node last used in a onDropEnter or onDropMove call. <br/>
     * - The {@link DropFeedback} returned by the last onDropEnter or onDropMove call. <br/>
     */
    public Closure paintClosure;

    /**
     * When set to a non-null valid rectangle, this informs the engine that a drag'n'drop
     * feedback wants to capture the mouse as long as it stays in the given area.
     * <p/>
     * When the mouse is captured, drop events will keep going to the rule that started the
     * capture and the current INode proxy will not change.
     */
    public Rect captureArea;

    /**
     * Set to true by the drag'n'drop engine when the current drag operation is a copy.
     * When false the operation is a move and <em>after</em> a successful drop the source
     * elements will be deleted.
     */
    public boolean isCopy;

    /**
     * Set to true when the drag'n'drop starts and ends in the same canvas of the
     * same Eclipse instance.
     */
    public boolean sameCanvas;

    /**
     * Initializes the drop feedback with the given user data and paint closure.
     * A paint is requested if the paint closure is non-null.
     */
    public DropFeedback(Object userData, Closure paintClosure) {
        this.userData = userData;
        this.paintClosure = paintClosure;
        this.requestPaint = paintClosure != null;
        this.captureArea = null;
    }
}
