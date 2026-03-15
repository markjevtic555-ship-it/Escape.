package com.escape.app;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.escape.app.utils.LocaleHelper;
import com.google.firebase.FirebaseApp;

public class EscapeApplication extends Application {

    private static final String TAG = "EscapeApplication";

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.setLocale(base));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        

        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this);
            Log.d(TAG, "Firebase initialized successfully");
        } else {
            Log.d(TAG, "Firebase already initialized");
        }
    }
}




