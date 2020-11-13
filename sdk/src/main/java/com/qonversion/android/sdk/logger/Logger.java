package com.qonversion.android.sdk.logger;

public interface Logger {

    void log(String tag, String message);

    void log(String message);

    void debug(String message);

}
