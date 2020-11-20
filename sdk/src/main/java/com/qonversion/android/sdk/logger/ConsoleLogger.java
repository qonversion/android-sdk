package com.qonversion.android.sdk.logger;

import android.util.Log;

import com.qonversion.android.sdk.BuildConfig;

public class ConsoleLogger implements Logger {

    private static final String TAG = "Qonversion";

    @Override
    public void release(String message) {
        log(TAG, message);
    }

    @Override
    public void debug(String message) {
        if (BuildConfig.DEBUG) {
            log(TAG, message);
        }
    }

    @Override
    public void debug(String tag, String message) {
        if (BuildConfig.DEBUG) {
            log(tag, message);
        }
    }

    private void log(String tag, String message){
        Log.println(Log.DEBUG, tag, format(message));
    }

    private String format(final String message) {
        return "Thread - " + Thread.currentThread().getName() + " " + message;
    }
}
