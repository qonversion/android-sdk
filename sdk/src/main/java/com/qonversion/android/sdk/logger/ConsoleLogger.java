package com.qonversion.android.sdk.logger;

import android.util.Log;

public class ConsoleLogger implements Logger {

    private static final String TAG = "Qonversion";

    @Override
    public void log(String tag, String message) {
        Log.println(Log.DEBUG, tag, format(message));
    }

    @Override
    public void log(String message) {
        log(TAG, message);
    }

    private String format(final String message) {
        return "Thread - " + Thread.currentThread().getName() + " " + message;
    }
}
