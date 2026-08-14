package com.qonversion.android.sdk.internal.remoteconfig

import com.qonversion.android.sdk.internal.storage.Cache
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.nio.ByteBuffer
import java.security.MessageDigest

private const val REMOTE_CONFIG_SESSION_PREFIX = "qonversion_remote_config_v2_session_"
private const val REMOTE_CONFIG_PROJECT_ID_PREFIX = "qonversion_remote_config_v2_project_"
private const val REMOTE_CONFIG_PROJECT_ID_MAX_CHARS = 32
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

/**
 * Addresses one stored session.
 *
 * The snapshot [scope] alone is not enough: the gateway mints a session for a specific anonymous
 * [userUid], and the canonical user id of the scope is a separate notion that can in principle
 * stay put while the anonymous uid is re-minted. Keying on both means a session can only ever be
 * replayed for the exact identity it was issued to.
 */
internal data class RemoteConfigSessionKey(
    val scope: RemoteConfigSnapshotScope,
    val userUid: String,
)

internal interface RemoteConfigSessionStore {
    fun load(key: RemoteConfigSessionKey): RemoteConfigGatewaySession?
    fun save(key: RemoteConfigSessionKey, session: RemoteConfigGatewaySession): Boolean
    fun clear(key: RemoteConfigSessionKey): Boolean
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
    override fun load(key: RemoteConfigSessionKey): RemoteConfigGatewaySession? {
        val storageKey = remoteConfigSessionStorageKey(key)
        val raw = try {
            cache.getString(storageKey, null)
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
            removeInvalid(storageKey)
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
    override fun save(key: RemoteConfigSessionKey, session: RemoteConfigGatewaySession): Boolean {
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
                values = mapOf(remoteConfigSessionStorageKey(key) to raw),
                removedKeys = emptySet(),
            )
        } catch (_: Exception) {
            false
        }
    }

    @Synchronized
    override fun clear(key: RemoteConfigSessionKey): Boolean = try {
        cache.updateStringsDurably(emptyMap(), setOf(remoteConfigSessionStorageKey(key)))
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

/**
 * Outcome of offering a bootstrapped project id to [RemoteConfigProjectIdRegistry].
 *
 * There is deliberately no "re-learned" outcome: the project id is the one piece of envelope
 * addressing that is stable for the lifetime of an installation, so a gateway answering with a
 * different one is a routing or configuration fault, never a legitimate rotation.
 */
internal enum class RemoteConfigProjectIdOutcome {
    Established,

    /** The offered id disagrees with the one already established. Permanent. */
    Conflict,

    /** The offered id could never address anything. Says nothing about the established one. */
    Unusable,
}

/** Durable storage of the project id a bootstrap established for one project key + environment. */
internal interface RemoteConfigProjectIdStore {
    fun load(scope: RemoteConfigSnapshotScope): Long?
    fun save(scope: RemoteConfigSnapshotScope, projectId: Long): Boolean
}

/**
 * Remembers the numeric project id the gateway bootstrapped, so a served snapshot can be checked
 * against something the SDK learned rather than something the app typed.
 *
 * The record is keyed by project key + environment and NOT by the canonical user id: the project id
 * addresses the app's project, not one identity inside it. Keying it per identity would both forget
 * the pin on every login and hide the case worth catching — one identity's session being answered
 * for a different project than another's.
 *
 * The first bootstrap establishes the value; every later one must agree with it. The
 * in-memory pin is authoritative for the process even when the durable write fails, so a storage
 * failure can never downgrade a conflict into a silent re-learn.
 */
internal class RemoteConfigProjectIdRegistry(private val store: RemoteConfigProjectIdStore) {
    private val established = mutableMapOf<RemoteConfigProjectIdScope, Long>()

    @Synchronized
    fun establish(scope: RemoteConfigSnapshotScope, projectId: Long): RemoteConfigProjectIdOutcome {
        // Reported apart from a conflict on purpose: an id that addresses nothing is a malformed
        // answer, not evidence that this installation is talking to the wrong project.
        if (projectId <= 0) return RemoteConfigProjectIdOutcome.Unusable
        val known = establishedLocked(scope)
        return when {
            known == projectId -> RemoteConfigProjectIdOutcome.Established
            known != null -> RemoteConfigProjectIdOutcome.Conflict
            else -> {
                established[RemoteConfigProjectIdScope.from(scope)] = projectId
                try {
                    store.save(scope, projectId)
                } catch (_: Exception) {
                    // The in-process pin still fences this run; the next start re-establishes it.
                }
                RemoteConfigProjectIdOutcome.Established
            }
        }
    }

    private fun establishedLocked(scope: RemoteConfigSnapshotScope): Long? {
        val key = RemoteConfigProjectIdScope.from(scope)
        return established[key] ?: loadPersisted(scope)?.also { established[key] = it }
    }

    private fun loadPersisted(scope: RemoteConfigSnapshotScope): Long? = try {
        store.load(scope)
    } catch (_: Exception) {
        null
    }?.takeIf { it > 0 }
}

private data class RemoteConfigProjectIdScope(val projectKey: String, val environment: String) {
    companion object {
        fun from(scope: RemoteConfigSnapshotScope) =
            RemoteConfigProjectIdScope(scope.projectKey, scope.environment)
    }
}

/**
 * Cache-backed [RemoteConfigProjectIdStore].
 *
 * Mirrors [PersistentRemoteConfigSessionStore]: the storage key is a salted digest, so the project
 * key never lands in a preference name. The value is a plain decimal, and anything that does not
 * read back as a positive number is treated as absent rather than trusted.
 */
internal class PersistentRemoteConfigProjectIdStore(private val cache: Cache) : RemoteConfigProjectIdStore {
    @Synchronized
    override fun load(scope: RemoteConfigSnapshotScope): Long? {
        val raw = try {
            cache.getString(remoteConfigProjectIdStorageKey(scope), null)
        } catch (_: Exception) {
            null
        } ?: return null
        return raw.takeIf { it.length <= REMOTE_CONFIG_PROJECT_ID_MAX_CHARS }?.trim()?.toLongOrNull()?.takeIf { it > 0 }
    }

    @Synchronized
    override fun save(scope: RemoteConfigSnapshotScope, projectId: Long): Boolean {
        if (projectId <= 0) return false
        return try {
            cache.updateStringsDurably(
                values = mapOf(remoteConfigProjectIdStorageKey(scope) to projectId.toString()),
                removedKeys = emptySet(),
            )
        } catch (_: Exception) {
            false
        }
    }
}

private fun remoteConfigProjectIdStorageKey(scope: RemoteConfigSnapshotScope): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.updateLengthPrefixed("remote-config-gateway-project-id-v1".encodeToByteArray())
    digest.updateLengthPrefixed(scope.projectKey.encodeToByteArray())
    digest.updateLengthPrefixed(scope.environment.encodeToByteArray())
    return REMOTE_CONFIG_PROJECT_ID_PREFIX + digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

private fun remoteConfigSessionStorageKey(key: RemoteConfigSessionKey): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.updateLengthPrefixed("remote-config-gateway-session-v1".encodeToByteArray())
    digest.updateLengthPrefixed(key.scope.projectKey.encodeToByteArray())
    digest.updateLengthPrefixed(key.scope.environment.encodeToByteArray())
    digest.updateLengthPrefixed(key.scope.canonicalUserId.encodeToByteArray())
    digest.updateLengthPrefixed(key.userUid.encodeToByteArray())
    return REMOTE_CONFIG_SESSION_PREFIX + digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

private fun MessageDigest.updateLengthPrefixed(value: ByteArray) {
    update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value.size).array())
    update(value)
}
