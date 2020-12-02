package com.telit.app_teacher;

import android.app.Application;

public class Myapp extends Application {
    private static final String TAG = "MyApplication";
    private static Myapp myApplication;

    public static Myapp getInstance() {
        return myApplication;
    }
}
