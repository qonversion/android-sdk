package com.qonversion.android.sdk

import com.qonversion.android.sdk.dto.QRemoteConfigFallbackValue
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigSnapshot
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigSubscription
import com.qonversion.android.sdk.listeners.QRemoteConfigUpdateListener
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigActivationCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigFetchCallback

/**
 * The Remote Config v2 snapshot API.
 *
 * The model is fetch/activate, not fetch/serve: a fetch only makes a release *available*, and
 * [activate] swaps the whole release atomically into [current]. Values therefore never change
 * under a running screen unless the app asks for it — or unless the release itself declares the
 * immediate apply policy, in which case the SDK performs the same whole-release swap on admission
 * and notifies [subscribeOnConfigUpdate] listeners.
 *
 * Every callback of this API is delivered on the main thread, exactly once. Reads ([current],
 * [fallbackRemoteConfigValue]) are synchronous and safe from any thread.
 *
 * The API is dormant unless the app passes a `QRemoteConfigV2Config` to
 * `QonversionConfig.Builder.setRemoteConfigV2Config`. While dormant there is no release at all:
 * fetches complete with `NotConfigured`, [current] is empty (it does **not** fall back to the
 * bundled defaults, because there is no scope to resolve them for), subscriptions never fire, and
 * [fallbackRemoteConfigValue] keeps answering because it reads the app asset directly.
 */
@ExperimentalQonversionApi
interface QRemoteConfigSnapshots {

    /**
     * The release that is currently activated.
     *
     * Reading before the first [activate] is a supported but flagged path: in a debug build the
     * SDK reports it loudly (read-before-activate), and in a release build it silently performs a
     * single implicit activation so the app is never served an empty config by accident.
     */
    val current: QRemoteConfigSnapshot

    /**
     * Fetches a release using the SDK's default timeout.
     *
     * @param callback delivered with the best available data — freshly fetched, previously
     * activated, or bundled — and the fetch status.
     */
    fun fetch(callback: QonversionRemoteConfigFetchCallback)

    /**
     * Fetches a release, giving up on *waiting* after [timeoutMs].
     *
     * On timeout the callback fires with `TimedOut` and the best available snapshot, while the
     * request itself keeps running: if it succeeds later, the release is admitted as usual and
     * becomes available to the next [activate].
     *
     * @param timeoutMs how long to wait for the completion, in milliseconds. A non-positive value
     * waives the caller's own deadline; the SDK still applies an internal ceiling (30 seconds), so
     * a completion always arrives.
     */
    fun fetch(timeoutMs: Long, callback: QonversionRemoteConfigFetchCallback)

    /**
     * Atomically swaps the last fetched release into [current].
     *
     * @param callback delivered with `changed = true` when the activated release differs from the
     * previously activated one.
     */
    fun activate(callback: QonversionRemoteConfigActivationCallback)

    /** Runs [fetch] and then [activate], delivering the activation result. */
    fun fetchAndActivate(callback: QonversionRemoteConfigActivationCallback)

    /** [fetchAndActivate] with an explicit fetch timeout — see [fetch]. */
    fun fetchAndActivate(timeoutMs: Long, callback: QonversionRemoteConfigActivationCallback)

    /**
     * Reads a value directly from the Remote Config defaults bundled with the app.
     *
     * Synchronous and independent of networking, identity, caches and activation, so it answers
     * before the first fetch or activate. Returns `null` when the key is absent from the bundle or
     * the bundle failed strict validation.
     */
    fun fallbackRemoteConfigValue(contextKey: String): QRemoteConfigFallbackValue?

    /**
     * Subscribes to config updates: the changed-key diff plus the release that became current.
     *
     * While the pipeline is dormant the subscription is inert: nothing is ever fetched or
     * activated, so no update can be delivered.
     *
     * @return a handle to stop receiving updates.
     */
    fun subscribeOnConfigUpdate(listener: QRemoteConfigUpdateListener): QRemoteConfigSubscription
}
