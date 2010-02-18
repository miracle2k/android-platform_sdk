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
     * Initializes the drop feedback with the given user data and paint closure.
     * A paint is requested if the paint closure is non-null.
     */
    public DropFeedback(Object userData, Closure paintClosure) {
        this.userData = userData;
        this.paintClosure = paintClosure;
        this.requestPaint = paintClosure != null;
    }
}
