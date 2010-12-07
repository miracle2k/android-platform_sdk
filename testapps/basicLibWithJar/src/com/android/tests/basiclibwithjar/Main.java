package com.android.tests.basiclibwithjar;

import com.android.tests.basicjar.Foo;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class Main extends Activity
{
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        Foo foo = new Foo();
        int a = foo.getRandomFoo();

        TextView tv = (TextView) findViewById(R.id.text);
        tv.setText("Random number from Jar: " + a);

    }
}
