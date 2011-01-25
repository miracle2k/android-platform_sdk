package com.android.tests.basicProjectWithJava;

import com.android.tests.basicJavaProject.Foo;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class Main extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        Foo foo = new Foo();

        TextView tv = (TextView) findViewById(R.id.text);
        tv.setText("basicProjectWithJava\nvalue from java project:" + foo.getRandomFoo());
    }
}