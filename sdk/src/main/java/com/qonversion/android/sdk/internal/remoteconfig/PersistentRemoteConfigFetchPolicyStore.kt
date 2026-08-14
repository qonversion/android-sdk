package com.qonversion.android.sdk.internal.remoteconfig

import com.qonversion.android.sdk.internal.storage.Cache
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.nio.ByteBuffer
import java.security.MessageDigest

private const val REMOTE_CONFIG_FETCH_POLICY_PREFIX = "qonversion_remote_config_v2_fetch_policy_"
private const val REMOTE_CONFIG_FETCH_POLICY_VERSION = 1
private const val REMOTE_CONFIG_FETCH_POLICY_MAX_BYTES = 1_024
private const val REMOTE_CONFIG_FETCH_POLICY_MAX_FAILURES = 63

internal class PersistentRemoteConfigFetchPolicyStore(
    private val cache: Cache,
    moshi: Moshi,
) : RemoteConfigFetchPolicyStore {
    private val adapter = moshi.adapter(PersistedRemoteConfigFetchPolicyState::class.java).failOnUnknown()

    @Synchronized
    @Suppress("ReturnCount")
    override fun load(scope: RemoteConfigFetchPolicyScope): RemoteConfigFetchPolicyState? {
        val key = remoteConfigFetchPolicyStorageKey(scope)
        val raw = try {
            cache.getString(key, null)
        } catch (_: Exception) {
            null
        } ?: return null
        val persisted = try {
            raw.takeIf { it.toByteArray(Charsets.UTF_8).size <= REMOTE_CONFIG_FETCH_POLICY_MAX_BYTES }
                ?.let(adapter::fromJson)
        } catch (_: Exception) {
            null
        }
        if (persisted == null || !persisted.isValid()) {
            removeInvalid(key)
            return null
        }
        return RemoteConfigFetchPolicyState(
            lastSuccessfulFetchAtMillis = persisted.lastSuccessfulFetchAtMillis,
            consecutiveRetryableFailures = persisted.consecutiveRetryableFailures,
            nextAllowedFetchAtMillis = persisted.nextAllowedFetchAtMillis,
        )
    }

    @Synchronized
    @Suppress("ReturnCount")
    override fun save(scope: RemoteConfigFetchPolicyScope, state: RemoteConfigFetchPolicyState): Boolean {
        val persisted = PersistedRemoteConfigFetchPolicyState(
            version = REMOTE_CONFIG_FETCH_POLICY_VERSION,
            lastSuccessfulFetchAtMillis = state.lastSuccessfulFetchAtMillis,
            consecutiveRetryableFailures = state.consecutiveRetryableFailures,
            nextAllowedFetchAtMillis = state.nextAllowedFetchAtMillis,
        )
        if (!persisted.isValid()) return false
        val raw = try {
            adapter.toJson(persisted)
        } catch (_: Exception) {
            return false
        }
        if (raw.toByteArray(Charsets.UTF_8).size > REMOTE_CONFIG_FETCH_POLICY_MAX_BYTES) return false
        return try {
            cache.updateStringsDurably(
                values = mapOf(remoteConfigFetchPolicyStorageKey(scope) to raw),
                removedKeys = emptySet(),
            )
        } catch (_: Exception) {
            false
        }
    }

    private fun PersistedRemoteConfigFetchPolicyState.isValid(): Boolean =
        version == REMOTE_CONFIG_FETCH_POLICY_VERSION &&
            lastSuccessfulFetchAtMillis >= 0 &&
            consecutiveRetryableFailures in 0..REMOTE_CONFIG_FETCH_POLICY_MAX_FAILURES &&
            nextAllowedFetchAtMillis >= 0

    private fun removeInvalid(key: String) {
        try {
            cache.updateStringsDurably(emptyMap(), setOf(key))
        } catch (_: Exception) {
            // The malformed state remains untrusted even when best-effort cleanup fails.
        }
    }
}

@JsonClass(generateAdapter = true)
internal data class PersistedRemoteConfigFetchPolicyState(
    val version: Int,
    @Json(name = "last_successful_fetch_at_millis")
    val lastSuccessfulFetchAtMillis: Long,
    @Json(name = "consecutive_retryable_failures")
    val consecutiveRetryableFailures: Int,
    @Json(name = "next_allowed_fetch_at_millis")
    val nextAllowedFetchAtMillis: Long,
)

private fun remoteConfigFetchPolicyStorageKey(scope: RemoteConfigFetchPolicyScope): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.updateLengthPrefixed("remote-config-fetch-policy-v1".encodeToByteArray())
    digest.updateLengthPrefixed(scope.projectKey.encodeToByteArray())
    digest.updateLengthPrefixed(scope.environment.encodeToByteArray())
    return REMOTE_CONFIG_FETCH_POLICY_PREFIX + digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

private fun MessageDigest.updateLengthPrefixed(value: ByteArray) {
    update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value.size).array())
    update(value)
}
