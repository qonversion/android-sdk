package com.qonversion.android.sdk.logger

class StubLogger : Logger {
    override fun log(tag: String?, message: String?) {
        // do nothing
    }

    override fun log(message: String?) {
        // do nothing
    }
}
