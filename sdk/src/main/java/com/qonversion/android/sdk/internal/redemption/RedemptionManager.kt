package com.qonversion.android.sdk.internal.redemption

import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.qonversion.android.sdk.dto.redemption.RedemptionResult
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.api.Api
import com.qonversion.android.sdk.internal.dto.redemption.RedeemResponse
import com.qonversion.android.sdk.internal.dto.request.RedeemReissueRequest
import com.qonversion.android.sdk.internal.dto.request.RedeemRequest
import com.qonversion.android.sdk.internal.dto.request.RedeemStatusRequest
import com.qonversion.android.sdk.internal.enqueue
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.storage.Cache
import com.qonversion.android.sdk.listeners.QonversionRedemptionCallback
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import retrofit2.Response

/**
 * Coordinates Web2App redemption: parses the email App Link, calls
 * `POST /v4/web/redeem`, disambiguates 409 via `/v4/web/redeem/status`, and on
 * success triggers an entitlements refresh so the server-side grant is
 * reflected on the device.
 *
 * Web2App M1.5 is grant-first: the backend attaches the entitlement to the
 * `app_uid` the SDK sends, and the redeem response is `{redeemed, app_uid}`
 * with no user id. The SDK therefore does NOT identify/merge any client user
 * id after redeem — it only refreshes entitlements.
 *
 * The reissue endpoint is also exposed here so the reissue dialog UI doesn't
 * need its own networking — it forwards through this manager.
 *
 * All public callbacks are dispatched on the main thread.
 *
 * Note: the entitlements refresh is invoked through the supplied
 * [refreshEntitlements] lambda rather than a direct reference to the product
 * center manager so this class stays unit-testable without spinning up the
 * full DI graph.
 */
internal class RedemptionManager(
    private val api: Api,
    private val internalConfig: InternalConfig,
    private val logger: Logger,
    // Persistent store for per-token Idempotency-Keys so dedup survives a
    // cold-start, not just an in-process retry (see [idempotencyKeyForToken]).
    private val cache: Cache,
    private val refreshEntitlements: () -> Unit,
) {

    private val handler = Handler(Looper.getMainLooper())

    /**
     * Token of the redemption currently in flight, or `null` when idle. Guards
     * against a double/race redemption (e.g. `onCreate` + `onNewIntent`, or a
     * fast double tap) firing two parallel `POST /v4/web/redeem` calls and two
     * entitlement refreshes. Reset to `null` on every terminal [deliver].
     */
    private val inFlightToken = AtomicReference<String?>(null)

    /**
     * Parses [uri] (expected: `https://screens.qonversion.io/r/{project_uid}/{token}`),
     * POSTs `/v4/web/redeem`, and invokes [callback] on the main thread with the
     * terminal [RedemptionResult]. See [RedemptionResult] for case semantics.
     */
    // Sequential precondition guard clauses (token, app_uid, in-flight) read
    // clearer as early returns than nested conditionals; exceeds detekt's
    // default ReturnCount of 2 by design.
    @Suppress("ReturnCount")
    fun handleRedemptionLink(uri: Uri, callback: QonversionRedemptionCallback) {
        val token = extractToken(uri)
        if (token == null) {
            // Never log the full Uri — the redemption token lives in the path and
            // would leak into LogCat / crash reporters. Log only scheme+host.
            logger.error(
                "RedemptionManager: malformed or untrusted redemption Uri " +
                    "(scheme=${uri.scheme}, host=${uri.host}) — no valid token segment found."
            )
            deliver(callback, RedemptionResult.InvalidToken)
            return
        }

        // Fail-fast on a missing app_uid (parity with iOS). Redeem tokens are
        // single-use: firing a redeem without app_uid would burn the token
        // server-side with no user to attach the grant to. If the SDK has no
        // usable app_uid yet (e.g. user-info bootstrap not finished), surface a
        // retryable outcome and issue NO network request.
        val appUid = internalConfig.uid.takeIf { it.isNotBlank() }
        if (appUid == null) {
            logger.error("RedemptionManager: no app_uid available yet — skipping redeem (retryable).")
            deliver(callback, RedemptionResult.Retryable)
            return
        }

        // In-flight guard: if a redemption is already running, reject this call
        // so we don't fire a second POST / second refresh. CAS null -> token.
        // The callback MUST still fire (Retryable) — a silent drop here strands
        // the host UI on an eternal spinner. Note we do NOT route this through
        // [deliver]: the guard belongs to the redemption already in flight and
        // must not be released by this rejected call.
        if (!inFlightToken.compareAndSet(null, token)) {
            logger.error("RedemptionManager: redemption already in flight — rejecting duplicate link as retryable.")
            postToMainThread { callback.onResult(RedemptionResult.Retryable) }
            return
        }

        val request = RedeemRequest(
            token = token,
            appUid = appUid,
        )

        // One Idempotency-Key (UUIDv4) per *logical* redeem — not per HTTP
        // attempt (parity with iOS). The same key is sent on the redeem POST and
        // any 409→/status recovery call. Persisted per token so a redeem retried
        // after the app was killed/relaunched reuses the same key and the backend
        // can still dedup — i.e. dedup survives cold-start, not just in-process.
        val idempotencyKey = idempotencyKeyForToken(token)

        api.redeem(request, idempotencyKey).enqueue {
            onResponse = { response ->
                onRedeemResponse(response, token, idempotencyKey, callback)
            }
            onFailure = { t ->
                logger.error("RedemptionManager: redeem network failure: $t")
                deliver(callback, RedemptionResult.NetworkError)
            }
        }
    }

    // The post-redeem entitlements refresh is best-effort: any failure there
    // must not fail the already-successful redeem, so we intentionally catch
    // broadly and only log.
    @Suppress("TooGenericExceptionCaught")
    private fun onRedeemResponse(
        response: Response<RedeemResponse>,
        token: String,
        idempotencyKey: String,
        callback: QonversionRedemptionCallback,
    ) {
        when {
            response.isSuccessful -> {
                // Grant-first: the server already attached the entitlement to
                // app_uid. We do NOT identify/merge a client user id; we only
                // refresh entitlements so the grant shows up on this device.
                // Best-effort — refresh failure must not fail the already-
                // successful redeem.
                try {
                    refreshEntitlements()
                } catch (t: Throwable) {
                    logger.error("RedemptionManager: entitlements refresh after redeem threw: $t")
                }
                deliver(callback, RedemptionResult.Success)
            }
            response.code() == HTTP_CONFLICT -> {
                // Disambiguate consumed vs other 409 via /redeem/status. Same
                // logical redeem → reuse the same Idempotency-Key.
                resolveConflict(token, idempotencyKey, callback)
            }
            response.code() == HTTP_NOT_FOUND -> {
                deliver(callback, RedemptionResult.InvalidToken)
            }
            response.code() == HTTP_GONE -> {
                deliver(callback, RedemptionResult.TokenExpired)
            }
            else -> {
                // 429 (rate limit), 5xx (server error) and any other non-mapped
                // 4xx (401/403/etc.): the server was reachable and responded, so
                // this is NOT a "no network" condition. Surface Retryable
                // (parity with iOS) so the host shows a back-off/retry
                // affordance, not a misleading offline error.
                logger.error(
                    "RedemptionManager: redeem reachable but non-success ${response.code()} — " +
                        "treating as Retryable."
                )
                deliver(callback, RedemptionResult.Retryable)
            }
        }
    }

    /**
     * POSTs `/v4/web/redeem/reissue` for [email]. Maps HTTP status to one of
     * the [ReissueResult] cases used by the dialog UI to render the right hint.
     */
    fun requestReissue(email: String, callback: (ReissueResult) -> Unit) {
        // Reissue is its own logical operation → its own fresh Idempotency-Key.
        api.redeemReissue(RedeemReissueRequest(email), UUID.randomUUID().toString()).enqueue {
            onResponse = { response ->
                val mapped = when {
                    response.isSuccessful -> ReissueResult.Sent
                    response.code() == HTTP_TOO_MANY_REQUESTS -> ReissueResult.RateLimited
                    response.code() in HTTP_SERVER_ERROR_RANGE -> ReissueResult.ServerError
                    else -> ReissueResult.ServerError
                }
                deliverReissue(callback, mapped)
            }
            onFailure = { t ->
                logger.error("RedemptionManager: reissue network failure: $t")
                deliverReissue(callback, ReissueResult.ServerError)
            }
        }
    }

    private fun resolveConflict(
        token: String,
        idempotencyKey: String,
        callback: QonversionRedemptionCallback,
    ) {
        api.redeemStatus(RedeemStatusRequest(token), idempotencyKey).enqueue {
            onResponse = { response ->
                val body = response.body()
                val result = when {
                    !response.isSuccessful -> RedemptionResult.AlreadyConsumed
                    body?.consumed == true -> RedemptionResult.AlreadyConsumed
                    body?.expired == true -> RedemptionResult.TokenExpired
                    else -> RedemptionResult.AlreadyConsumed
                }
                deliver(callback, result)
            }
            onFailure = { t ->
                // If status check itself fails, surface the original 409 as
                // AlreadyConsumed — the host UI guidance for both states is
                // "request a new email", which matches AlreadyConsumed.
                logger.error("RedemptionManager: redeem/status failure: $t")
                deliver(callback, RedemptionResult.AlreadyConsumed)
            }
        }
    }

    // Sequential guard clauses (scheme, segment count, prefix, token) read
    // clearer as early returns than nested conditionals; exceeds detekt's
    // default ReturnCount of 2 by design.
    @Suppress("ReturnCount")
    private fun extractToken(uri: Uri): String? {
        // Spec rule RT2-W3: only the verified https App Link form
        // (`android:autoVerify="true"`) is allowed as an email-link
        // transport. Any installed app can claim a `qonversion://`
        // intent-filter and hijack the redemption token, so we reject any
        // non-https scheme up-front — before any token extraction or
        // network call. The custom scheme is reserved for in-process
        // host→SDK forwarding and is not exercised by this entry point.
        if (!uri.scheme.equals(REDEEM_SCHEME_HTTPS, ignoreCase = true)) return null

        // Host pinning: even over https, only the canonical App Link host is a
        // trusted email-link transport. A link like
        // `https://attacker.com/r/{proj}/{token}` would otherwise leak the
        // redemption token to a third-party backend. Reject any other host
        // up-front — before any token extraction or network call.
        if (!uri.host.equals(REDEEM_HOST, ignoreCase = true)) return null

        val segments = uri.pathSegments ?: return null
        // Expected: /r/{project_uid}/{token}
        if (segments.size < REDEEM_PATH_MIN_SEGMENTS) return null
        if (segments[0] != REDEEM_PATH_PREFIX) return null

        val token = segments.getOrNull(REDEEM_TOKEN_SEGMENT_INDEX)?.trim()
        return token?.takeIf { it.isNotBlank() }
    }

    /**
     * Returns a stable Idempotency-Key for the logical redeem of [token],
     * generating and persisting a fresh UUID the first time and reusing it on
     * any later attempt for the same token — so dedup survives a cold-start, not
     * just an in-process retry.
     *
     * The token itself is a secret carried in the email link; we never persist
     * it in clear. The cache entry is keyed by a SHA-256 of the token so the
     * raw token never lands on disk while still keying by token identity.
     */
    private fun idempotencyKeyForToken(token: String): String {
        val cacheKey = IDEMPOTENCY_KEY_PREFIX + sha256(token)
        cache.getString(cacheKey, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val key = UUID.randomUUID().toString()
        cache.putString(cacheKey, key)
        return key
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun deliver(callback: QonversionRedemptionCallback, result: RedemptionResult) {
        // Terminal point of a redemption — release the in-flight guard so a
        // later, legitimate redemption can proceed. Safe no-op when the guard
        // was never taken (e.g. malformed/untrusted Uri short-circuit).
        inFlightToken.set(null)
        postToMainThread { callback.onResult(result) }
    }

    private fun deliverReissue(callback: (ReissueResult) -> Unit, result: ReissueResult) {
        postToMainThread { callback(result) }
    }

    private fun postToMainThread(runnable: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable()
        } else {
            handler.post(runnable)
        }
    }

    /** Internal-only reissue outcome surfaced to the dialog. */
    internal enum class ReissueResult {
        Sent,
        RateLimited,
        ServerError,
    }

    private companion object {
        const val HTTP_CONFLICT = 409
        const val HTTP_NOT_FOUND = 404
        const val HTTP_GONE = 410
        const val HTTP_TOO_MANY_REQUESTS = 429
        val HTTP_SERVER_ERROR_RANGE = 500..599

        const val REDEEM_PATH_PREFIX = "r"
        // Path segments are 0-indexed after the leading "/", so /r/{project}/{token}
        // → segments = ["r", "{project}", "{token}"], token is at index 2.
        const val REDEEM_TOKEN_SEGMENT_INDEX = 2
        const val REDEEM_PATH_MIN_SEGMENTS = 3
        // RT2-W3: only https App Links are accepted as email-link transport.
        const val REDEEM_SCHEME_HTTPS = "https"
        // Canonical App Link host. Token transport is pinned to this host so a
        // foreign https host can't exfiltrate the redemption token.
        const val REDEEM_HOST = "screens.qonversion.io"
        // Prefix for the persisted per-token Idempotency-Key entries.
        const val IDEMPOTENCY_KEY_PREFIX = "com.qonversion.web2app.redeem.idempotency."
    }
}
