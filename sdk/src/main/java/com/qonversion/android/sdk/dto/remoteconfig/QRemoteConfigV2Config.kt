package com.qonversion.android.sdk.dto.remoteconfig

import com.qonversion.android.sdk.ExperimentalQonversionApi

private const val REMOTE_CONFIG_V2_UID_MAX_CODE_POINTS = 36

/** Receives one short-lived host-signed assertion requested by Remote Config v2. */
@ExperimentalQonversionApi
fun interface QRemoteConfigIdentifyAssertionCallback {
    /** Pass `null` when no assertion can be obtained; the SDK then fails closed. */
    fun onResult(assertion: String?)
}

/**
 * Obtains a short-lived assertion from the app's authenticated backend for an identified user.
 *
 * The SDK calls this only when it must mint a Remote Config session for an existing identified
 * account. Implementations may complete asynchronously and must never put the host signing key in
 * the app. The assertion is opaque to the SDK and is sent only to the configured gateway's
 * `/v3/remote-config-v2/session/identify` endpoint.
 */
@ExperimentalQonversionApi
fun interface QRemoteConfigIdentifyAssertionProvider {
    fun requestAssertion(externalUserId: String, callback: QRemoteConfigIdentifyAssertionCallback)
}

/**
 * Enables the experimental Remote Config v2 snapshot pipeline.
 *
 * The pipeline is **dormant** unless this configuration is passed to
 * `QonversionConfig.Builder.setRemoteConfigV2Config`: without it the SDK builds no v2 store, opens
 * no v2 connection, and `QRemoteConfigSnapshots` answers every fetch with
 * [QRemoteConfigFetchStatus.NotConfigured] while still serving bundled defaults. There is no
 * default base URL and no production endpoint is contacted implicitly.
 *
 * The targeting context a snapshot was resolved for is deliberately **not** configured here, and no
 * future version will ask for it. The fingerprint hashes mutable targeting context (app/OS version,
 * locale, purchases, properties); it rotates legitimately and MUST NOT be pinned across fetches.
 * Identity isolation is the session's job: every snapshot read travels on a session token minted
 * for exactly one identity, the gateway routes on that session, and the SDK stores each identity's
 * releases under its own scoped storage key.
 *
 * The numeric project id is deliberately **not** configured here either, although a served snapshot
 * is checked against one. Unlike the fingerprint it is stable, but the app is not its source: the
 * SDK learns it from the gateway's session bootstrap, pins the first value it is ever told, and
 * treats a later bootstrap that answers with a different one as a hard failure.
 *
 * That is a trade, not a strict improvement: the check no longer proves a snapshot belongs to the
 * project the developer meant to target — the first bootstrap is trusted — it proves that every
 * snapshot and every later session agree with the first one. What it buys is that a value the app
 * could only ever get wrong is gone, and the property that actually protects a user — a snapshot
 * being served for the session that asked for it — is enforced against the server's own answer.
 *
 * @param baseUrl base URL of the Remote Config v2 gateway, e.g. `https://host/`. The SDK appends
 * its own paths, so a bare origin is expected.
 * @param environmentUid uid of the Remote Config environment to read.
 * @param minFetchIntervalSeconds minimum interval between real network fetches, in seconds.
 * `0` (the default) means "auto": the production default interval in a release build and no
 * throttling at all in a debuggable one, so a developer iterating on an environment sees every
 * change. An explicit positive value wins over auto in both build modes. Forced fetches bypass
 * the interval either way, and failure backoff applies independently of it.
 * @param identifyAssertionProvider obtains a host-signed assertion when `identify()` switches to
 * an existing account. Without it anonymous Remote Config continues to work, while an identified
 * session that needs proof of identity fails closed instead of sending a bare external user id.
 * @throws IllegalArgumentException if any value is malformed.
 */
@ExperimentalQonversionApi
class QRemoteConfigV2Config @JvmOverloads constructor(
    val baseUrl: String,
    val environmentUid: String,
    val minFetchIntervalSeconds: Long = 0,
    val identifyAssertionProvider: QRemoteConfigIdentifyAssertionProvider? = null,
) {
    init {
        require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
            "Remote Config v2 base url must be an absolute http(s) url"
        }
        require(
            environmentUid.isNotEmpty() &&
                environmentUid.codePointCount(0, environmentUid.length) <= REMOTE_CONFIG_V2_UID_MAX_CODE_POINTS,
        ) { "Remote Config v2 environment uid must be 1..$REMOTE_CONFIG_V2_UID_MAX_CODE_POINTS code points" }
        require(minFetchIntervalSeconds >= 0) {
            "Remote Config v2 minimum fetch interval must not be negative"
        }
    }
}
