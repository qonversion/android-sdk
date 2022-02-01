package com.qonversion.android.sdk.internal.utils.workers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class DelayedWorkerImpl(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : DelayedWorker {

    internal var job: Job? = null

    override fun doDelayed(delayMs: Long, ignoreExistingJob: Boolean, action: suspend () -> Unit) {
        if (ignoreExistingJob || !isInProgress()) {
            job = scope.launch {
                delay(delayMs)
                action()
            }
        }
    }

    override fun doImmediately(action: suspend () -> Unit) {
        cancel()
        job = scope.launch {
            action()
        }
    }

    override fun cancel() {
        job?.cancel()
        job = null
    }

    override fun isInProgress(): Boolean {
        job.let { immutableJob ->
            return immutableJob != null && immutableJob.isActive
        }
    }
}
