package com.android.tests.basicjar;

import java.util.Random;

public class Foo {
    public static int FOO = 42;

    public int getFoo() {
        return FOO;
    }

    public int getRandomFoo() {
        Random r = new Random(System.currentTimeMillis());
        return r.nextInt(FOO);
    }
}