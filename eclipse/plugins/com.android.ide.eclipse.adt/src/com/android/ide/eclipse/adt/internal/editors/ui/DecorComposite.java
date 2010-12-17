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

package com.android.ide.eclipse.adt.internal.editors.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ToolBar;

/**
 * A composite that wraps a control, with a header composed of an image/label
 * and a set of toolbar icons.
 */
public class DecorComposite extends Composite {

    private CLabel mTitle;
    private ToolBar mToolbar;
    private IDecorContent mContent;

    public DecorComposite(Composite parent, int style) {
        super(parent, style);

        GridLayoutBuilder.create(this).noMargins().columns(2);

        mTitle = new CLabel(this, SWT.NONE);
        GridDataBuilder.create(mTitle).hGrab().hFill().vCenter();

        mToolbar = new ToolBar(this, SWT.FLAT | SWT.RIGHT);
        GridDataBuilder.create(mToolbar).fill();

        Label sep = new Label(this, SWT.SEPARATOR | SWT.HORIZONTAL);
        GridDataBuilder.create(sep).hSpan(2).hFill();
    }

    public DecorComposite setTitle(String title) {
        mTitle.setText(title);
        return this;
    }

    public DecorComposite setImage(Image image) {
        mTitle.setImage(image);
        return this;
    }

    public DecorComposite setContent(IDecorContent content) {
        mContent = content;
        content.createControl(this);
        GridDataBuilder.create(content.getControl()).hSpan(2).grab().fill();

        String t = content.getTitle();
        if (t != null) {
            setTitle(t);
        }

        Image i = content.getImage();
        if (i != null) {
            setImage(i);
        }

        return this;
    }

    public Control getContentControl() {
        return mContent == null ? null : mContent.getControl();
    }

}
