package com.android.tests.basicProjectWithJava;

import com.android.tests.basicJavaProject.Foo;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

public class MyTextView extends TextView {

    public MyTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        Foo f = new Foo();
        setText("Foo: " + f.getRandomFoo());
    }

    public MyTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Foo f = new Foo();
        setText("Foo: " + f.getRandomFoo());
    }

    public MyTextView(Context context) {
        super(context);
    }
}
