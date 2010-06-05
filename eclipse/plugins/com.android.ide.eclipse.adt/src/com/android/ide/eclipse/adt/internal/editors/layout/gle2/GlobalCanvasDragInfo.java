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

package com.android.ide.eclipse.adt.internal.editors.layout.gle2;

/**
 * This singleton is used to keep track of drag'n'drops initiated within this
 * session of Eclipse. A drag can be initiated from a palette or from a canvas
 * and its content is an Android View fully-qualified class name.
 * <p/>
 * Overall this is a workaround: the issue is that the drag'n'drop SWT API does not
 * allow us to know the transfered data during the initial drag -- only when the
 * data is dropped do we know what it is about (and to be more exact there is a workaround
 * to do just that which works on Windows but not on Linux/Mac SWT).
 * <p/>
 * In the GLE we'd like to adjust drag feedback to the data being actually dropped.
 * The singleton instance of this class will be used to track the data currently dragged
 * off a canvas or its palette and then set back to null when the drag'n'drop is finished.
 * <p/>
 * Note that when a drag starts in one instance of Eclipse and the dragOver/drop is done
 * in a <em>separate</em> instance of Eclipse, the tragged FQCN won't be registered here
 * and will be null.
 */
class GlobalCanvasDragInfo {

    private static final GlobalCanvasDragInfo sInstance = new GlobalCanvasDragInfo();

    private SimpleElement[] mCurrentElements = null;
    private CanvasSelection[] mCurrentSelection;
    private Object mSourceCanvas = null;

    /** Private constructor. Use {@link #getInstance()} to retrieve the singleton. */
    private GlobalCanvasDragInfo() {
        // pass
    }

    /** Returns the singleton instance. */
    public static GlobalCanvasDragInfo getInstance() {
        return sInstance;
    }

    /** Registers the XML elements being dragged. */
    public void startDrag(SimpleElement[] elements, CanvasSelection[] selection, Object sourceCanvas) {
        mCurrentElements = elements;
        mCurrentSelection = selection;
        mSourceCanvas = sourceCanvas;
    }

    /** Unregisters elements being dragged. */
    public void stopDrag() {
        mCurrentElements = null;
        mSourceCanvas = null;
    }

    /** Returns the elements being dragged. */
    public SimpleElement[] getCurrentElements() {
        return mCurrentElements;
    }

    /** Returns the selection originally dragged.
     * Can be null if the drag did not start in a canvas.
     */
    public CanvasSelection[] getCurrentSelection() {
        return mCurrentSelection;
    }

    /**
     * Returns the object that call {@link #startDrag(SimpleElement[], CanvasSelection[], Object)}.
     * Can be null.
     * This is not meant to access the object indirectly, it is just meant to compare if the
     * source and the destination of the drag'n'drop are the same, so object identity
     * is all what matters.
     */
    public Object getSourceCanvas() {
        return mSourceCanvas;
    }
}
