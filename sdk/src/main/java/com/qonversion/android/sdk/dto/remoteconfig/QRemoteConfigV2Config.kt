package com.qonversion.android.sdk.dto.remoteconfig

import com.qonversion.android.sdk.ExperimentalQonversionApi

private const val REMOTE_CONFIG_V2_UID_MAX_CODE_POINTS = 36
private val LOWERCASE_SHA256_PATTERN = Regex("^[0-9a-f]{64}$")

/**
 * Enables the experimental Remote Config v2 snapshot pipeline.
 *
 * The pipeline is **dormant** unless this configuration is passed to
 * `QonversionConfig.Builder.setRemoteConfigV2Config`: without it the SDK builds no v2 store, opens
 * no v2 connection, and `QRemoteConfigSnapshots` answers every fetch with
 * [QRemoteConfigFetchStatus.NotConfigured] while still serving bundled defaults. There is no
 * default base URL and no production endpoint is contacted implicitly.
 *
 * @param baseUrl base URL of the Remote Config v2 gateway, e.g. `https://host/`. The SDK appends
 * its own paths, so a bare origin is expected.
 * @param environmentUid uid of the Remote Config environment to read.
 * @param projectId numeric project id the served snapshots must belong to.
 * @param contextFingerprint the snapshot context fingerprint the gateway resolves for this
 * integration. It binds an admitted snapshot to the targeting context it was resolved for, and the
 * SDK cannot derive it — the value is server-side keyed. It is a temporary integration hand-off:
 * once the gateway returns the fingerprint on session bootstrap, this parameter goes away.
 * @throws IllegalArgumentException if any value is malformed.
 */
@ExperimentalQonversionApi
class QRemoteConfigV2Config(
    val baseUrl: String,
    val environmentUid: String,
    val projectId: Long,
    val contextFingerprint: String,
) {
    init {
        require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
            "Remote Config v2 base url must be an absolute http(s) url"
        }
        require(
            environmentUid.isNotEmpty() &&
                environmentUid.codePointCount(0, environmentUid.length) <= REMOTE_CONFIG_V2_UID_MAX_CODE_POINTS,
        ) { "Remote Config v2 environment uid must be 1..$REMOTE_CONFIG_V2_UID_MAX_CODE_POINTS code points" }
        require(projectId > 0) { "Remote Config v2 project id must be positive" }
        require(LOWERCASE_SHA256_PATTERN.matches(contextFingerprint)) {
            "Remote Config v2 context fingerprint must be 64 lowercase hexadecimal characters"
        }
    }
}
