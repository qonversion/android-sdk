package com.qonversion.android.app;

import androidx.multidex.MultiDexApplication;

import com.qonversion.android.sdk.Qonversion;

public class App extends MultiDexApplication {
    @Override
    public void onCreate() {
        super.onCreate();
        Qonversion.initialize(
                this,
                BuildConfig.QONVERSION_API_KEY,
                ""
        );
    }
}
