package com.qonversion.android.sdk.internal.remoteconfig

import com.qonversion.android.sdk.internal.storage.Cache
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.nio.ByteBuffer
import java.security.MessageDigest

private const val REMOTE_CONFIG_SESSION_PREFIX = "qonversion_remote_config_v2_session_"
private const val REMOTE_CONFIG_SESSION_VERSION = 1
private const val REMOTE_CONFIG_SESSION_MAX_BYTES = 4 * 1024
private const val REMOTE_CONFIG_SESSION_TOKEN_MAX_BYTES = 2 * 1024

/**
 * A Remote Config v2 gateway session obtained from the bootstrap route.
 *
 * The session token authorises snapshot reads for exactly one identity scope. It is
 * intentionally *not* a device-wide credential: it is stored and looked up per
 * [RemoteConfigSnapshotScope], so an identity switch can never reuse the previous
 * identity's token.
 */
internal data class RemoteConfigGatewaySession(
    val token: String,
    val projectId: Long,
    val environment: String,
    val expiresAtMillis: Long,
) {
    fun isUsableAt(nowMillis: Long): Boolean =
        token.isNotEmpty() && projectId > 0 && environment.isNotEmpty() && nowMillis < expiresAtMillis
}

internal interface RemoteConfigSessionStore {
    fun load(scope: RemoteConfigSnapshotScope): RemoteConfigGatewaySession?
    fun save(scope: RemoteConfigSnapshotScope, session: RemoteConfigGatewaySession): Boolean
    fun clear(scope: RemoteConfigSnapshotScope): Boolean
}

/**
 * Durable, per-identity-scope session storage.
 *
 * Mirrors [PersistentRemoteConfigFetchPolicyStore]: the storage key is a salted digest of the
 * scope, so neither the project key nor the canonical user id ever lands in a preference name,
 * and a scope change simply addresses a different record.
 */
internal class PersistentRemoteConfigSessionStore(
    private val cache: Cache,
    moshi: Moshi,
) : RemoteConfigSessionStore {
    private val adapter = moshi.adapter(PersistedRemoteConfigGatewaySession::class.java)

    @Synchronized
    @Suppress("ReturnCount")
    override fun load(scope: RemoteConfigSnapshotScope): RemoteConfigGatewaySession? {
        val key = remoteConfigSessionStorageKey(scope)
        val raw = try {
            cache.getString(key, null)
        } catch (_: Exception) {
            null
        } ?: return null
        val persisted = try {
            raw.takeIf { it.toByteArray(Charsets.UTF_8).size <= REMOTE_CONFIG_SESSION_MAX_BYTES }
                ?.let(adapter::fromJson)
        } catch (_: Exception) {
            null
        }
        if (persisted == null || !persisted.isValid()) {
            removeInvalid(key)
            return null
        }
        return RemoteConfigGatewaySession(
            token = persisted.token,
            projectId = persisted.projectId,
            environment = persisted.environment,
            expiresAtMillis = persisted.expiresAtMillis,
        )
    }

    @Synchronized
    @Suppress("ReturnCount")
    override fun save(scope: RemoteConfigSnapshotScope, session: RemoteConfigGatewaySession): Boolean {
        val persisted = PersistedRemoteConfigGatewaySession(
            version = REMOTE_CONFIG_SESSION_VERSION,
            token = session.token,
            projectId = session.projectId,
            environment = session.environment,
            expiresAtMillis = session.expiresAtMillis,
        )
        if (!persisted.isValid()) return false
        val raw = try {
            adapter.toJson(persisted)
        } catch (_: Exception) {
            return false
        }
        if (raw.toByteArray(Charsets.UTF_8).size > REMOTE_CONFIG_SESSION_MAX_BYTES) return false
        return try {
            cache.updateStringsDurably(
                values = mapOf(remoteConfigSessionStorageKey(scope) to raw),
                removedKeys = emptySet(),
            )
        } catch (_: Exception) {
            false
        }
    }

    @Synchronized
    override fun clear(scope: RemoteConfigSnapshotScope): Boolean = try {
        cache.updateStringsDurably(emptyMap(), setOf(remoteConfigSessionStorageKey(scope)))
    } catch (_: Exception) {
        false
    }

    private fun PersistedRemoteConfigGatewaySession.isValid(): Boolean =
        version == REMOTE_CONFIG_SESSION_VERSION &&
            token.isNotEmpty() &&
            token.toByteArray(Charsets.UTF_8).size <= REMOTE_CONFIG_SESSION_TOKEN_MAX_BYTES &&
            projectId > 0 &&
            environment.isNotEmpty() &&
            expiresAtMillis > 0

    private fun removeInvalid(key: String) {
        try {
            cache.updateStringsDurably(emptyMap(), setOf(key))
        } catch (_: Exception) {
            // A malformed session stays untrusted even when best-effort cleanup fails.
        }
    }
}

@JsonClass(generateAdapter = true)
internal data class PersistedRemoteConfigGatewaySession(
    val version: Int,
    @Json(name = "session_token")
    val token: String,
    @Json(name = "project_id")
    val projectId: Long,
    val environment: String,
    @Json(name = "expires_at_millis")
    val expiresAtMillis: Long,
)

private fun remoteConfigSessionStorageKey(scope: RemoteConfigSnapshotScope): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.updateLengthPrefixed("remote-config-gateway-session-v1".encodeToByteArray())
    digest.updateLengthPrefixed(scope.projectKey.encodeToByteArray())
    digest.updateLengthPrefixed(scope.environment.encodeToByteArray())
    digest.updateLengthPrefixed(scope.canonicalUserId.encodeToByteArray())
    return REMOTE_CONFIG_SESSION_PREFIX + digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

private fun MessageDigest.updateLengthPrefixed(value: ByteArray) {
    update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value.size).array())
    update(value)
}
