package com.qonversion.android.sdk.internal.utils.workers

internal interface DelayedWorker {

    fun doDelayed(delayMs: Long, action: suspend () -> Unit)

    fun doImmediately(action: suspend () -> Unit)

    fun cancel()

    fun isInProgress(): Boolean
}
