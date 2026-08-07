package com.qonversion.android.sdk.dto.remoteconfig

import com.qonversion.android.sdk.ExperimentalQonversionApi

private const val REMOTE_CONFIG_V2_UID_MAX_CODE_POINTS = 36

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
 * @param baseUrl base URL of the Remote Config v2 gateway, e.g. `https://host/`. The SDK appends
 * its own paths, so a bare origin is expected.
 * @param environmentUid uid of the Remote Config environment to read.
 * @param projectId numeric project id the served snapshots must belong to.
 * @throws IllegalArgumentException if any value is malformed.
 */
@ExperimentalQonversionApi
class QRemoteConfigV2Config(
    val baseUrl: String,
    val environmentUid: String,
    val projectId: Long,
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
    }
}
