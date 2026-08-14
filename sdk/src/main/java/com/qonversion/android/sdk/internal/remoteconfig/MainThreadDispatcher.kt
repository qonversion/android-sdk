package com.qonversion.android.sdk.internal.remoteconfig

import android.os.Handler
import android.os.Looper

/**
 * Delivers Remote Config callbacks on the main thread.
 *
 * Mirrors `QonversionInternal.postToMainThread`: work already on the main thread runs inline, so a
 * callback issued from the main thread is not deferred to the next loop iteration.
 */
internal class MainThreadDispatcher : RemoteConfigMainDispatcher {
    private val handler = Handler(Looper.getMainLooper())

    override fun post(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            handler.post(action)
        }
    }

    override fun postDeferred(action: () -> Unit) {
        handler.post(action)
    }
}
