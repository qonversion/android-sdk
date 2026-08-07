@file:OptIn(ExperimentalQonversionApi::class)

package com.qonversion.android.sdk.internal.remoteconfig

import com.qonversion.android.sdk.ExperimentalQonversionApi
import com.qonversion.android.sdk.QRemoteConfigSnapshots
import com.qonversion.android.sdk.dto.QRemoteConfigFallbackValue
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigActivationResult
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigFetchResult
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigFetchStatus
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigSnapshot
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigSubscription
import com.qonversion.android.sdk.listeners.QRemoteConfigUpdateListener
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigActivationCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigFetchCallback

/**
 * Adapts [RemoteConfigV2Manager] to the public [QRemoteConfigSnapshots] surface.
 *
 * A `null` [manager] is the dormant configuration: no v2 store, no connection, no scope. There is
 * no release to read, so [current] is empty; [fallbackRemoteConfigValue] still answers, because the
 * bundled defaults are an app asset rather than part of the pipeline. Every fetch completes with
 * [QRemoteConfigFetchStatus.NotConfigured] instead of silently doing nothing.
 */
internal class QRemoteConfigSnapshotsImpl(
    internal val manager: RemoteConfigV2Manager?,
    private val bundledValueReader: (String) -> QRemoteConfigFallbackValue?,
    private val mainDispatcher: RemoteConfigMainDispatcher,
) : QRemoteConfigSnapshots {

    override val current: QRemoteConfigSnapshot
        get() = manager?.current ?: emptySnapshot()

    override fun fetch(callback: QonversionRemoteConfigFetchCallback) = runFetch(null, callback)

    override fun fetch(timeoutMs: Long, callback: QonversionRemoteConfigFetchCallback) =
        runFetch(timeoutMs, callback)

    override fun activate(callback: QonversionRemoteConfigActivationCallback) {
        val target = manager ?: return mainDispatcher.post {
            callback.onResult(notConfiguredActivation(null))
        }
        target.activate { result -> callback.onResult(result) }
    }

    override fun fetchAndActivate(callback: QonversionRemoteConfigActivationCallback) =
        runFetchAndActivate(null, callback)

    override fun fetchAndActivate(timeoutMs: Long, callback: QonversionRemoteConfigActivationCallback) =
        runFetchAndActivate(timeoutMs, callback)

    override fun fallbackRemoteConfigValue(contextKey: String): QRemoteConfigFallbackValue? =
        bundledValueReader(contextKey)

    override fun subscribeOnConfigUpdate(listener: QRemoteConfigUpdateListener): QRemoteConfigSubscription {
        val target = manager ?: return QRemoteConfigSubscription { }
        return target.subscribeOnConfigUpdate { update -> listener.onRemoteConfigUpdated(update) }
    }

    private fun runFetch(timeoutMs: Long?, callback: QonversionRemoteConfigFetchCallback) {
        val target = manager ?: return mainDispatcher.post {
            callback.onResult(QRemoteConfigFetchResult(QRemoteConfigFetchStatus.NotConfigured, emptySnapshot()))
        }
        target.fetch(timeoutMs) { result -> callback.onResult(result) }
    }

    private fun runFetchAndActivate(timeoutMs: Long?, callback: QonversionRemoteConfigActivationCallback) {
        val target = manager ?: return mainDispatcher.post {
            callback.onResult(notConfiguredActivation(QRemoteConfigFetchStatus.NotConfigured))
        }
        target.fetchAndActivate(timeoutMs) { result -> callback.onResult(result) }
    }

    private fun notConfiguredActivation(fetchStatus: QRemoteConfigFetchStatus?) = QRemoteConfigActivationResult(
        changed = false,
        snapshot = emptySnapshot(),
        fetchStatus = fetchStatus,
    )

    private fun emptySnapshot() = QRemoteConfigSnapshot(
        RemoteConfigSnapshot(primaryRelease = null, previousRelease = null, bundledRelease = null),
    )
}
