package com.qonversion.android.sdk.logger;

public interface Logger {

    void release(String message);

    void debug(String message);

    void debug(String tag, String message);

}
