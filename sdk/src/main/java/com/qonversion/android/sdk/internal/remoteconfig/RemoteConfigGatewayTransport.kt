package com.qonversion.android.sdk.internal.remoteconfig

import com.qonversion.android.sdk.internal.logger.Logger
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean

internal const val REMOTE_CONFIG_SESSION_PATH = "v3/remote-config-v2/session"
internal const val REMOTE_CONFIG_SNAPSHOT_PATH = "v3/remote-config-v2/snapshot"
internal const val REMOTE_CONFIG_SESSION_HEADER = "X-Qonversion-RC-Session"

private const val REMOTE_CONFIG_USER_UID_MAX_BYTES = 255
private const val REMOTE_CONFIG_SESSION_TOKEN_HEADER_MAX_BYTES = 512
private const val REMOTE_CONFIG_CLIENT_CONTEXT_SCALAR_MAX_BYTES = 256
private const val REMOTE_CONFIG_SESSION_EXPIRY_SKEW_MILLIS = 30_000L
private const val MILLIS_PER_SECOND = 1_000L
private const val HTTP_OK = 200
private const val HTTP_NOT_MODIFIED = 304
private const val HTTP_UNAUTHORIZED = 401

/**
 * Device-scoped facts the gateway needs to evaluate targeting.
 *
 * [deviceInstalledAtSeconds] is a DEVICE fact, not an identity fact: the server evaluates
 * account age as `min(device_installed_at, client.created_at)`, so a fresh anonymous client row
 * minted after a logout looks "new" and only the preserved device install date keeps a
 * long-standing user out of "new users" targeting. Producers of this value must therefore read it
 * from a device-scoped source that is unaffected by identify/logout — see
 * [DeviceRemoteConfigClientContextProvider], whose constructor deliberately takes no identity.
 */
internal data class RemoteConfigClientContext(
    val platform: String,
    val appVersion: String,
    val osVersion: String,
    val sdkVersion: String,
    val locale: String,
    val deviceModel: String,
    val deviceInstalledAtSeconds: Long,
) {
    internal fun isValid(): Boolean = deviceInstalledAtSeconds >= 0 &&
        scalars().all { it.isNotEmpty() && it.isWithinScalarBudget() }

    private fun scalars() = listOf(platform, appVersion, osVersion, sdkVersion, locale, deviceModel)

    private fun String.isWithinScalarBudget(): Boolean =
        toByteArray(Charsets.UTF_8).size <= REMOTE_CONFIG_CLIENT_CONTEXT_SCALAR_MAX_BYTES
}

/**
 * Supplies the device-scoped client context for every snapshot request.
 *
 * Implementations MUST NOT derive [RemoteConfigClientContext.deviceInstalledAtSeconds] from
 * anything that is reset by an identity change.
 */
internal fun interface RemoteConfigClientContextProvider {
    fun clientContext(): RemoteConfigClientContext?
}

/**
 * Everything the transport needs to address one identity: the snapshot [scope] the session is
 * stored under, the SDK project token used as the bearer credential, and the anonymous SDK uid
 * the bootstrap route mints a session for.
 */
internal data class RemoteConfigTransportIdentity(
    val scope: RemoteConfigSnapshotScope,
    val projectToken: String,
    val userUid: String,
) {
    internal fun isValid(): Boolean = projectToken.isNotEmpty() &&
        projectToken.trim() == projectToken &&
        userUid.isNotEmpty() &&
        userUid.toByteArray(Charsets.UTF_8).size <= REMOTE_CONFIG_USER_UID_MAX_BYTES &&
        !userUid.contains(UNICODE_REPLACEMENT_CHARACTER)

    private companion object {
        const val UNICODE_REPLACEMENT_CHARACTER = '�'
    }
}

internal fun interface RemoteConfigTransportIdentityProvider {
    fun currentIdentity(): RemoteConfigTransportIdentity?
}

/**
 * Binds [RemoteConfigFetchCoordinator]'s transport seam to the internal Remote Config v2 gateway.
 *
 * Responsibilities, in the order the coordinator observes them:
 * 1. Bootstrap on a missing (or expired) session — `POST {base}/v3/remote-config-v2/session`.
 * 2. Read the snapshot — `POST {base}/v3/remote-config-v2/snapshot` with the session header and,
 *    when the coordinator holds a conditional validator, the exact `If-None-Match` value.
 * 3. Re-bootstrap exactly once on a snapshot `401`, then retry the snapshot once. A second `401`
 *    is a typed failure, never another bootstrap — the flow cannot loop.
 * 4. Hand the response body to the coordinator as the EXACT bytes received, paired with the exact
 *    `ETag` header. Nothing is decoded, re-encoded or charset-converted on the way in.
 *
 * The [callFactory] must NOT carry the legacy `NetworkInterceptor`: this transport owns its
 * request headers (including `Authorization`) and a second interceptor-provided value would be
 * appended rather than replaced.
 *
 * Neither the project token nor the session token is ever logged.
 */
@Suppress("LongParameterList")
internal class RemoteConfigGatewayTransport(
    private val callFactory: Call.Factory,
    private val baseUrlProvider: () -> String,
    private val identityProvider: RemoteConfigTransportIdentityProvider,
    private val clientContextProvider: RemoteConfigClientContextProvider,
    private val sessionStore: RemoteConfigSessionStore,
    private val clock: RemoteConfigFetchClock,
    moshi: Moshi,
    private val logger: Logger,
) : RemoteConfigFetchTransport {
    private val bootstrapRequestAdapter = moshi.adapter(RemoteConfigSessionRequest::class.java)
    private val bootstrapResponseAdapter = moshi.adapter(RemoteConfigSessionResponse::class.java)
    private val snapshotRequestAdapter = moshi.adapter(RemoteConfigSnapshotRequest::class.java)

    private val lock = Any()
    private var cachedScope: RemoteConfigSnapshotScope? = null
    private var cachedSession: RemoteConfigGatewaySession? = null

    override fun fetch(
        request: RemoteConfigFetchRequest,
        completion: (RemoteConfigFetchResponse) -> Unit,
    ) {
        val deliver = SingleDelivery(completion)
        val identity = identityProvider.currentIdentity()?.takeIf { it.isValid() }
        val context = clientContextProvider.clientContext()?.takeIf { it.isValid() }
        if (identity == null || context == null) {
            logger.debug("Remote Config v2 transport is not addressable yet")
            deliver(RemoteConfigFetchResponse.Failure())
            return
        }
        val session = loadUsableSession(identity.scope)
        if (session == null) {
            // Bootstrap-on-missing-session. The snapshot that follows a fresh mint may not
            // re-bootstrap on 401 — that is what keeps the flow finite.
            mint(identity, deliver) { minted ->
                requestSnapshot(identity, context, minted, request, deliver, allowReBootstrap = false)
            }
        } else {
            requestSnapshot(identity, context, session, request, deliver, allowReBootstrap = true)
        }
    }

    @Suppress("LongParameterList")
    private fun requestSnapshot(
        identity: RemoteConfigTransportIdentity,
        context: RemoteConfigClientContext,
        session: RemoteConfigGatewaySession,
        request: RemoteConfigFetchRequest,
        deliver: SingleDelivery,
        allowReBootstrap: Boolean,
    ) {
        val url = resolve(REMOTE_CONFIG_SNAPSHOT_PATH)
        val body = try {
            snapshotRequestAdapter.toJson(RemoteConfigSnapshotRequest(context.toWire()))
        } catch (_: Exception) {
            null
        }
        if (url == null || body == null) {
            deliver(RemoteConfigFetchResponse.Failure())
            return
        }
        val httpRequest = baseRequest(url, identity, body)
            .header(REMOTE_CONFIG_SESSION_HEADER, session.token)
            .apply {
                request.ifNoneMatch
                    ?.takeIf { it.isNotEmpty() && it.trim() == it }
                    ?.let { header("If-None-Match", it) }
            }
            .build()
        enqueue(httpRequest, deliver) { outcome ->
            onSnapshotOutcome(identity, context, request, deliver, allowReBootstrap, outcome)
        }
    }

    @Suppress("LongParameterList")
    private fun onSnapshotOutcome(
        identity: RemoteConfigTransportIdentity,
        context: RemoteConfigClientContext,
        request: RemoteConfigFetchRequest,
        deliver: SingleDelivery,
        allowReBootstrap: Boolean,
        outcome: HttpOutcome?,
    ) {
        when {
            outcome == null -> deliver(RemoteConfigFetchResponse.Failure())
            outcome.code == HTTP_OK -> deliver(outcome.asSuccessOrFailure())
            outcome.code == HTTP_NOT_MODIFIED ->
                deliver(RemoteConfigFetchResponse.NotModified(outcome.etag))
            outcome.code == HTTP_UNAUTHORIZED -> {
                forgetSession(identity.scope)
                if (!allowReBootstrap) {
                    logger.debug("Remote Config v2 snapshot stayed unauthorized after re-bootstrap")
                    deliver(RemoteConfigFetchResponse.Failure(statusCode = outcome.code))
                    return
                }
                reBootstrapOnce(identity, context, request, deliver)
            }
            else -> deliver(outcome.asFailure())
        }
    }

    private fun reBootstrapOnce(
        identity: RemoteConfigTransportIdentity,
        context: RemoteConfigClientContext,
        request: RemoteConfigFetchRequest,
        deliver: SingleDelivery,
    ) {
        mint(identity, deliver) { session ->
            requestSnapshot(identity, context, session, request, deliver, allowReBootstrap = false)
        }
    }

    private fun mint(
        identity: RemoteConfigTransportIdentity,
        deliver: SingleDelivery,
        onMinted: (RemoteConfigGatewaySession) -> Unit,
    ) {
        val url = resolve(REMOTE_CONFIG_SESSION_PATH)
        val body = try {
            bootstrapRequestAdapter.toJson(RemoteConfigSessionRequest(identity.userUid))
        } catch (_: Exception) {
            null
        }
        if (url == null || body == null) {
            deliver(RemoteConfigFetchResponse.Failure())
            return
        }
        enqueue(baseRequest(url, identity, body).build(), deliver) { outcome ->
            val session = outcome
                ?.takeIf { it.code == HTTP_OK }
                ?.body
                ?.let { readSession(it) }
            if (session == null) {
                logger.debug("Remote Config v2 session bootstrap failed with code ${outcome?.code}")
                // A 200 that does not carry a usable session is a contract violation, not a
                // status the fetch policy should reason about.
                deliver(if (outcome?.code == HTTP_OK) RemoteConfigFetchResponse.Failure() else outcome.asFailure())
                return@enqueue
            }
            rememberSession(identity.scope, session)
            onMinted(session)
        }
    }

    private fun baseRequest(
        url: HttpUrl,
        identity: RemoteConfigTransportIdentity,
        body: String,
    ): Request.Builder = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer ${identity.projectToken}")
        .header("Content-Type", JSON_CONTENT_TYPE)
        // The gateway answers `private, no-store`; declaring it on the request as well keeps a
        // shared OkHttp cache from ever synthesising a body the strict parser never saw.
        .header("Cache-Control", "no-store")
        .post(RequestBody.create(JSON_MEDIA_TYPE, body.toByteArray(Charsets.UTF_8)))

    /**
     * Every exit of this method must end in exactly one [deliver] call: the coordinator parks a
     * waiter on the callback, so a swallowed throw on an OkHttp dispatcher thread would strand it
     * until its timeout instead of failing fast.
     */
    private fun enqueue(request: Request, deliver: SingleDelivery, onOutcome: (HttpOutcome?) -> Unit) {
        fun handle(outcome: HttpOutcome?) = try {
            onOutcome(outcome)
        } catch (_: Throwable) {
            deliver(RemoteConfigFetchResponse.Failure())
        }

        val call = try {
            callFactory.newCall(request)
        } catch (_: Throwable) {
            handle(null)
            return
        }
        val callback = object : Callback {
            override fun onFailure(call: Call, e: IOException) = handle(null)

            override fun onResponse(call: Call, response: Response) {
                val outcome = try {
                    response.use { it.toOutcome() }
                } catch (_: Throwable) {
                    null
                }
                handle(outcome)
            }
        }
        try {
            call.enqueue(callback)
        } catch (_: Throwable) {
            handle(null)
        }
    }

    private fun Response.toOutcome(): HttpOutcome = HttpOutcome(
        code = code(),
        // `bytes()` is the raw octet stream: no charset decode, no re-encode, no JSON round trip.
        body = if (code() == HTTP_NOT_MODIFIED) null else body()?.bytes(),
        etag = header("ETag"),
        retryAfterMillis = header("Retry-After").parseRetryAfterMillis(),
    )

    @Suppress("ReturnCount")
    private fun loadUsableSession(scope: RemoteConfigSnapshotScope): RemoteConfigGatewaySession? {
        val now = nowMillis()
        synchronized(lock) {
            if (cachedScope == scope) {
                cachedSession?.let { return it.takeIf { session -> session.isUsable(now) } }
            }
        }
        val persisted = try {
            sessionStore.load(scope)
        } catch (_: Exception) {
            null
        } ?: return null
        if (!persisted.isUsable(now)) {
            forgetSession(scope)
            return null
        }
        synchronized(lock) {
            cachedScope = scope
            cachedSession = persisted
        }
        return persisted
    }

    private fun rememberSession(scope: RemoteConfigSnapshotScope, session: RemoteConfigGatewaySession) {
        synchronized(lock) {
            cachedScope = scope
            cachedSession = session
        }
        // A session whose expiry could not be trusted is used for this fetch only: persisting it
        // would hand a later cold start a credential we cannot reason about.
        if (session.expiresAtMillis <= nowMillis()) return
        try {
            sessionStore.save(scope, session)
        } catch (_: Exception) {
            // The in-memory session still serves this process; the next cold start re-bootstraps.
        }
    }

    private fun forgetSession(scope: RemoteConfigSnapshotScope) {
        synchronized(lock) {
            if (cachedScope == scope) {
                cachedSession = null
                cachedScope = null
            }
        }
        try {
            sessionStore.clear(scope)
        } catch (_: Exception) {
            // A stale record is re-validated (and dropped again) on the next load.
        }
    }

    @Suppress("ReturnCount")
    private fun readSession(body: ByteArray): RemoteConfigGatewaySession? {
        val parsed = try {
            bootstrapResponseAdapter.fromJson(body.toString(Charsets.UTF_8))
        } catch (_: Exception) {
            null
        } ?: return null
        val token = parsed.sessionToken ?: return null
        if (token.isEmpty() || token.trim() != token ||
            token.toByteArray(Charsets.UTF_8).size > REMOTE_CONFIG_SESSION_TOKEN_HEADER_MAX_BYTES
        ) {
            return null
        }
        val projectId = parsed.projectId ?: return null
        val environment = parsed.environment?.takeIf { it.isNotEmpty() } ?: return null
        if (projectId <= 0) return null
        return RemoteConfigGatewaySession(
            token = token,
            projectId = projectId,
            // The session environment ("prod") lives in a different namespace than the snapshot
            // scope environment uid, so it is recorded rather than compared.
            environment = environment,
            expiresAtMillis = parsed.expiresAt.parseRfc3339Millis() ?: 0,
        )
    }

    private fun RemoteConfigGatewaySession.isUsable(nowMillis: Long): Boolean =
        isUsableAt(nowMillis + REMOTE_CONFIG_SESSION_EXPIRY_SKEW_MILLIS)

    private fun resolve(path: String): HttpUrl? = try {
        HttpUrl.parse(baseUrlProvider())?.newBuilder()?.addPathSegments(path)?.build()
    } catch (_: Exception) {
        null
    }

    private fun nowMillis(): Long = try {
        clock.nowMillis().coerceAtLeast(0)
    } catch (_: Exception) {
        0
    }

    private fun RemoteConfigClientContext.toWire() = RemoteConfigClientContextWire(
        platform = platform,
        appVersion = appVersion,
        osVersion = osVersion,
        sdkVersion = sdkVersion,
        locale = locale,
        deviceModel = deviceModel,
        deviceInstalledAt = deviceInstalledAtSeconds,
    )

    private class SingleDelivery(
        private val completion: (RemoteConfigFetchResponse) -> Unit,
    ) : (RemoteConfigFetchResponse) -> Unit {
        private val delivered = AtomicBoolean(false)

        override fun invoke(response: RemoteConfigFetchResponse) {
            if (delivered.compareAndSet(false, true)) completion(response)
        }
    }

    private class HttpOutcome(
        val code: Int,
        val body: ByteArray?,
        val etag: String?,
        val retryAfterMillis: Long?,
    ) {
        fun asSuccessOrFailure(): RemoteConfigFetchResponse {
            val bytes = body
            val validator = etag
            return if (bytes == null || validator.isNullOrEmpty()) {
                // A 200 without a strong validator cannot be admitted, and it is not retryable.
                RemoteConfigFetchResponse.Failure()
            } else {
                RemoteConfigFetchResponse.Success(bytes, validator)
            }
        }
    }

    private companion object {
        const val JSON_CONTENT_TYPE = "application/json; charset=utf-8"
        val JSON_MEDIA_TYPE: MediaType? = MediaType.parse(JSON_CONTENT_TYPE)

        fun HttpOutcome?.asFailure() = RemoteConfigFetchResponse.Failure(
            statusCode = this?.code,
            retryAfterMillis = this?.retryAfterMillis,
        )
    }
}

private fun String?.parseRetryAfterMillis(): Long? =
    this?.trim()?.toLongOrNull()?.takeIf { it >= 0 }?.let { seconds ->
        if (seconds > Long.MAX_VALUE / MILLIS_PER_SECOND) Long.MAX_VALUE else seconds * MILLIS_PER_SECOND
    }

private fun String?.parseRfc3339Millis(): Long? {
    val value = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val normalized = value.replace("Z", "+0000").replace(Regex("([+\\-]\\d{2}):(\\d{2})$"), "$1$2")
    return RFC3339_FORMATS.firstNotNullOfOrNull { pattern ->
        val format = SimpleDateFormat(pattern, Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val position = ParsePosition(0)
        val parsed = format.parse(normalized, position)
        parsed?.takeIf { position.index == normalized.length }?.time
    }
}

private val RFC3339_FORMATS = listOf(
    "yyyy-MM-dd'T'HH:mm:ssZ",
    "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
)

@JsonClass(generateAdapter = true)
internal data class RemoteConfigSessionRequest(
    @Json(name = "user_uid") val userUid: String,
)

@JsonClass(generateAdapter = true)
internal data class RemoteConfigSessionResponse(
    @Json(name = "session_token") val sessionToken: String?,
    @Json(name = "project_id") val projectId: Long?,
    @Json(name = "environment") val environment: String?,
    @Json(name = "expires_at") val expiresAt: String?,
)

@JsonClass(generateAdapter = true)
internal data class RemoteConfigSnapshotRequest(
    @Json(name = "client_context") val clientContext: RemoteConfigClientContextWire,
)

@JsonClass(generateAdapter = true)
internal data class RemoteConfigClientContextWire(
    @Json(name = "platform") val platform: String,
    @Json(name = "app_version") val appVersion: String,
    @Json(name = "os_version") val osVersion: String,
    @Json(name = "sdk_version") val sdkVersion: String,
    @Json(name = "locale") val locale: String,
    @Json(name = "device_model") val deviceModel: String,
    @Json(name = "device_installed_at") val deviceInstalledAt: Long,
)
