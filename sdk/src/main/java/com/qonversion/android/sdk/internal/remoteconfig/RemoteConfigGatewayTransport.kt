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
import okhttp3.ResponseBody
import java.io.IOException
import java.util.GregorianCalendar
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean

internal const val REMOTE_CONFIG_SESSION_PATH = "v3/remote-config-v2/session"
internal const val REMOTE_CONFIG_SNAPSHOT_PATH = "v3/remote-config-v2/snapshot"
internal const val REMOTE_CONFIG_SESSION_HEADER = "X-Qonversion-RC-Session"
internal const val REMOTE_CONFIG_SNAPSHOT_BODY_MAX_BYTES = 8L * 1024 * 1024

private const val REMOTE_CONFIG_USER_UID_MAX_BYTES = 255
private const val REMOTE_CONFIG_SESSION_TOKEN_HEADER_MAX_BYTES = 512
private const val REMOTE_CONFIG_CLIENT_CONTEXT_SCALAR_MAX_BYTES = 256
private const val REMOTE_CONFIG_SESSION_EXPIRY_SKEW_MILLIS = 30_000L
private const val MILLIS_PER_SECOND = 1_000L
private const val HTTP_OK = 200
private const val HTTP_NOT_MODIFIED = 304
private const val HTTP_UNAUTHORIZED = 401
private const val ASCII_PRINTABLE_MIN = 0x20
private const val ASCII_PRINTABLE_MAX = 0x7e

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
 *
 * [projectToken] is validated as an HTTP header value, not merely as a non-empty string: it is
 * interpolated into `Authorization`, and OkHttp rejects a non-printable byte by throwing an
 * `IllegalArgumentException` whose message quotes the offending value — i.e. the credential.
 */
internal data class RemoteConfigTransportIdentity(
    val scope: RemoteConfigSnapshotScope,
    val projectToken: String,
    val userUid: String,
) {
    internal val sessionKey: RemoteConfigSessionKey get() = RemoteConfigSessionKey(scope, userUid)

    internal fun isValid(): Boolean = projectToken.isNotEmpty() &&
        projectToken.isHttpHeaderSafe() &&
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
 * 5. Publish the project id the session was minted for, which is what the admission check compares
 *    the envelope against. The bootstrap is the SDK's only source for it, so it is established here
 *    (see [RemoteConfigProjectIdRegistry]) rather than configured by the app, and a session whose
 *    project id contradicts the established one is refused as
 *    [RemoteConfigFetchResponse.ProjectMismatch] instead of being used for a read.
 *
 * The completion is invoked exactly once on every path, including one that throws on an OkHttp
 * dispatcher thread: the coordinator parks a waiter on it, and a lost completion would strand that
 * waiter until its (optional) timeout.
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
    private val projectIds: RemoteConfigProjectIdRegistry,
    private val clock: RemoteConfigFetchClock,
    moshi: Moshi,
    private val logger: Logger,
    private val maxSnapshotBodyBytes: Long = REMOTE_CONFIG_SNAPSHOT_BODY_MAX_BYTES,
) : RemoteConfigFetchTransport {
    private val bootstrapRequestAdapter = moshi.adapter(RemoteConfigSessionRequest::class.java)
    private val bootstrapResponseAdapter = moshi.adapter(RemoteConfigSessionResponse::class.java)
    private val snapshotRequestAdapter = moshi.adapter(RemoteConfigSnapshotRequest::class.java)

    private val lock = Any()
    private var cachedKey: RemoteConfigSessionKey? = null
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
        val session = loadUsableSession(identity.sessionKey)
        if (session == null) {
            // Bootstrap-on-missing-session. The snapshot that follows a fresh mint may not
            // re-bootstrap on 401 — that is what keeps the flow finite.
            mint(identity, deliver) { minted ->
                requestSnapshot(identity, context, minted, request, deliver, allowReBootstrap = false)
            }
        } else {
            establishProjectId(identity, session)?.let { refusal -> deliver(refusal) }
                ?: requestSnapshot(identity, context, session, request, deliver, allowReBootstrap = true)
        }
    }

    /**
     * Pins the project id this session was minted for, or returns the response that refuses it.
     *
     * A refused session is dropped rather than merely skipped for this fetch: it addresses a
     * project this installation has never read, so keeping it would replay the same refusal on
     * every later fetch.
     */
    private fun establishProjectId(
        identity: RemoteConfigTransportIdentity,
        session: RemoteConfigGatewaySession,
    ): RemoteConfigFetchResponse? {
        val outcome = projectIds.establish(identity.scope, session.projectId)
        if (outcome == RemoteConfigProjectIdOutcome.Established) return null
        forgetSession(identity.sessionKey)
        return if (outcome == RemoteConfigProjectIdOutcome.Conflict) {
            logger.error(
                "Remote Config v2 refused a gateway session: it was minted for a different " +
                    "project than the one this installation established",
            )
            RemoteConfigFetchResponse.ProjectMismatch
        } else {
            // A malformed answer, not an addressing fault: it stays an ordinary failure.
            logger.debug("Remote Config v2 refused a gateway session without a usable project id")
            RemoteConfigFetchResponse.Failure()
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
        val body = try {
            snapshotRequestAdapter.toJson(RemoteConfigSnapshotRequest(context.toWire()))
        } catch (_: Throwable) {
            null
        }
        val httpRequest = body?.let {
            buildRequest(REMOTE_CONFIG_SNAPSHOT_PATH, identity, it) { builder ->
                builder.header(REMOTE_CONFIG_SESSION_HEADER, session.token)
                request.ifNoneMatch
                    ?.takeIf { validator -> validator.isNotEmpty() && validator.isHttpHeaderSafe() }
                    ?.let { validator -> builder.header("If-None-Match", validator) }
            }
        }
        if (httpRequest == null) {
            deliver(RemoteConfigFetchResponse.Failure())
            return
        }
        enqueue(httpRequest, deliver) { outcome ->
            onSnapshotOutcome(identity, context, session, request, deliver, allowReBootstrap, outcome)
        }
    }

    @Suppress("LongParameterList")
    private fun onSnapshotOutcome(
        identity: RemoteConfigTransportIdentity,
        context: RemoteConfigClientContext,
        session: RemoteConfigGatewaySession,
        request: RemoteConfigFetchRequest,
        deliver: SingleDelivery,
        allowReBootstrap: Boolean,
        outcome: HttpOutcome?,
    ) {
        when {
            outcome == null -> deliver(RemoteConfigFetchResponse.Failure())
            // The project id travels with the session that authorised this exact read, so the
            // admission check compares the envelope against the session it was served for.
            outcome.code == HTTP_OK -> deliver(outcome.asSuccessOrFailure(session.projectId))
            outcome.code == HTTP_NOT_MODIFIED ->
                deliver(RemoteConfigFetchResponse.NotModified(outcome.etag))
            outcome.code == HTTP_UNAUTHORIZED -> {
                forgetSession(identity.sessionKey)
                if (!allowReBootstrap) {
                    logger.debug("Remote Config v2 snapshot stayed unauthorized after re-bootstrap")
                    deliver(RemoteConfigFetchResponse.Failure(statusCode = outcome.code))
                    return
                }
                mint(identity, deliver) { session ->
                    requestSnapshot(identity, context, session, request, deliver, allowReBootstrap = false)
                }
            }
            else -> deliver(outcome.asFailure())
        }
    }

    private fun mint(
        identity: RemoteConfigTransportIdentity,
        deliver: SingleDelivery,
        onMinted: (RemoteConfigGatewaySession) -> Unit,
    ) {
        val body = try {
            bootstrapRequestAdapter.toJson(RemoteConfigSessionRequest(identity.userUid))
        } catch (_: Throwable) {
            null
        }
        val httpRequest = body?.let { buildRequest(REMOTE_CONFIG_SESSION_PATH, identity, it) }
        if (httpRequest == null) {
            deliver(RemoteConfigFetchResponse.Failure())
            return
        }
        enqueue(httpRequest, deliver) { outcome ->
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
            // Established BEFORE the session is remembered: a session minted for a project this
            // installation has never read must not survive the fetch that revealed the conflict.
            establishProjectId(identity, session)?.let { refusal ->
                deliver(refusal)
                return@enqueue
            }
            rememberSession(identity.sessionKey, session)
            onMinted(session)
        }
    }

    /**
     * Builds a request, returning `null` instead of throwing. `Request.Builder.header` rejects
     * non-printable values by throwing, and this is reached from OkHttp callback threads.
     */
    private fun buildRequest(
        path: String,
        identity: RemoteConfigTransportIdentity,
        body: String,
        configure: (Request.Builder) -> Unit = {},
    ): Request? = try {
        val url = HttpUrl.parse(baseUrlProvider())?.newBuilder()?.addPathSegments(path)?.build()
        url?.let {
            Request.Builder()
                .url(it)
                .header("Authorization", "Bearer ${identity.projectToken}")
                .header("Content-Type", JSON_CONTENT_TYPE)
                .header("Accept", "application/json")
                // The gateway answers `private, no-store`; declaring it on the request as well
                // keeps a shared OkHttp cache from ever synthesising a body the parser never saw.
                .header("Cache-Control", "no-store")
                .post(RequestBody.create(JSON_MEDIA_TYPE, body.toByteArray(Charsets.UTF_8)))
                .also(configure)
                .build()
        }
    } catch (_: Throwable) {
        null
    }

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
        body = if (code() == HTTP_NOT_MODIFIED) null else body()?.readBounded(maxSnapshotBodyBytes),
        etag = header("ETag"),
        retryAfterMillis = header("Retry-After").parseRetryAfterMillis(),
    )

    /**
     * Reads at most [max] bytes as the raw octet stream: no charset decode, no re-encode, no JSON
     * round trip. A body over budget yields `null` rather than an unbounded allocation.
     */
    private fun ResponseBody.readBounded(max: Long): ByteArray? {
        val source = source()
        source.request(max + 1)
        return if (source.buffer().size > max) null else source.readByteArray()
    }

    @Suppress("ReturnCount")
    private fun loadUsableSession(key: RemoteConfigSessionKey): RemoteConfigGatewaySession? {
        val now = nowMillis()
        val cached = synchronized(lock) { cachedSession.takeIf { cachedKey == key } }
        if (cached != null && cached.isUsable(now)) return cached
        // A dead in-memory slot must not shadow the durable record, and a dead durable record must
        // be dropped rather than re-read on every fetch.
        val persisted = try {
            sessionStore.load(key)
        } catch (_: Throwable) {
            null
        }
        if (persisted == null || !persisted.isUsable(now)) {
            forgetSession(key)
            return null
        }
        synchronized(lock) {
            cachedKey = key
            cachedSession = persisted
        }
        return persisted
    }

    private fun rememberSession(key: RemoteConfigSessionKey, session: RemoteConfigGatewaySession) {
        synchronized(lock) {
            cachedKey = key
            cachedSession = session
        }
        // A session whose expiry could not be trusted is used for this fetch only: persisting it
        // would hand a later cold start a credential we cannot reason about.
        if (session.expiresAtMillis <= nowMillis()) return
        try {
            sessionStore.save(key, session)
        } catch (_: Throwable) {
            // The in-memory session still serves this process; the next cold start re-bootstraps.
        }
    }

    private fun forgetSession(key: RemoteConfigSessionKey) {
        synchronized(lock) {
            if (cachedKey == key) {
                cachedSession = null
                cachedKey = null
            }
        }
        try {
            sessionStore.clear(key)
        } catch (_: Throwable) {
            // A stale record is re-validated (and dropped again) on the next load.
        }
    }

    @Suppress("ReturnCount")
    private fun readSession(body: ByteArray): RemoteConfigGatewaySession? {
        val parsed = try {
            bootstrapResponseAdapter.fromJson(body.toString(Charsets.UTF_8))
        } catch (_: Throwable) {
            null
        } ?: return null
        val token = parsed.sessionToken ?: return null
        if (!token.isUsableSessionToken()) return null
        val projectId = parsed.projectId?.takeIf { it > 0 } ?: return null
        val environment = parsed.environment?.takeIf { it.isNotEmpty() } ?: return null
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

    private fun nowMillis(): Long = try {
        clock.nowMillis().coerceAtLeast(0)
    } catch (_: Throwable) {
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
        fun asSuccessOrFailure(projectId: Long): RemoteConfigFetchResponse {
            val bytes = body?.takeIf { it.isNotEmpty() }
            val validator = etag?.takeIf { it.isNotEmpty() }
            // An empty body, an over-budget body, a 200 without a strong validator or a session
            // carrying no usable project id cannot be admitted, and none of them is retryable.
            return if (bytes != null && validator != null && projectId > 0) {
                RemoteConfigFetchResponse.Success(bytes, validator, projectId)
            } else {
                RemoteConfigFetchResponse.Failure()
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

internal fun String.isHttpHeaderSafe(): Boolean =
    all { character -> character.code in ASCII_PRINTABLE_MIN..ASCII_PRINTABLE_MAX }

private fun String.isUsableSessionToken(): Boolean = isNotEmpty() &&
    trim() == this &&
    isHttpHeaderSafe() &&
    toByteArray(Charsets.UTF_8).size <= REMOTE_CONFIG_SESSION_TOKEN_HEADER_MAX_BYTES

private fun String?.parseRetryAfterMillis(): Long? =
    this?.trim()?.toLongOrNull()?.takeIf { it >= 0 }?.let { seconds ->
        if (seconds > Long.MAX_VALUE / MILLIS_PER_SECOND) Long.MAX_VALUE else seconds * MILLIS_PER_SECOND
    }

/**
 * RFC 3339 timestamps, as a Go gateway emits them.
 *
 * `time.Time` marshals as RFC3339Nano with trailing zeros stripped, so the fraction is 0-9 digits
 * wide rather than the 3 a `SimpleDateFormat` pattern can express, and RFC 3339 §5.6 allows a
 * lowercase `t`/`z`. Both are parsed here; anything else yields `null`, which the caller treats as
 * "expiry unknown" (usable for this fetch, never persisted).
 */
@Suppress("MagicNumber", "ReturnCount")
private fun String?.parseRfc3339Millis(): Long? {
    val match = RFC3339_PATTERN.matchEntire(this?.trim().orEmpty()) ?: return null
    val (year, month, day, hour, minute, second, fraction, sign, offsetHour, offsetMinute) =
        match.destructured
    val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
        isLenient = false
        clear()
        set(year.toInt(), month.toInt() - 1, day.toInt(), hour.toInt(), minute.toInt(), second.toInt())
    }
    val epochMillis = try {
        calendar.timeInMillis
    } catch (_: IllegalArgumentException) {
        return null
    }
    val fractionMillis = fraction.takeIf { it.isNotEmpty() }
        ?.padEnd(3, '0')?.substring(0, 3)?.toLong() ?: 0
    val offsetMillis = if (sign.isEmpty()) {
        0
    } else {
        val magnitude = (offsetHour.toLong() * 60 + offsetMinute.toLong()) * 60 * MILLIS_PER_SECOND
        if (sign == "-") -magnitude else magnitude
    }
    return epochMillis + fractionMillis - offsetMillis
}

private val RFC3339_PATTERN = Regex(
    "(\\d{4})-(\\d{2})-(\\d{2})[Tt](\\d{2}):(\\d{2}):(\\d{2})(?:\\.(\\d{1,9}))?" +
        "(?:[Zz]|([+\\-])(\\d{2}):(\\d{2}))",
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
