package com.qonversion.android.sdk.internal.redemption

import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.qonversion.android.sdk.dto.redemption.RedemptionResult
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.api.Api
import com.qonversion.android.sdk.internal.dto.request.RedeemReissueRequest
import com.qonversion.android.sdk.internal.dto.request.RedeemRequest
import com.qonversion.android.sdk.internal.dto.request.RedeemStatusRequest
import com.qonversion.android.sdk.internal.enqueue
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.listeners.QonversionRedemptionCallback

/**
 * Coordinates Web2App redemption: parses the email App Link, calls
 * `POST /v4/web/redeem`, disambiguates 409 via `/v4/web/redeem/status`, and
 * triggers the SDK identify-flow for the merge (RT4-W2).
 *
 * The reissue endpoint is also exposed here so the reissue dialog UI doesn't
 * need its own networking — it forwards through this manager.
 *
 * All public callbacks are dispatched on the main thread.
 *
 * Note: the SDK identify call is invoked through the supplied [identifyUser]
 * lambda rather than a direct reference to the product center manager so this
 * class stays unit-testable without spinning up the full DI graph.
 */
internal class RedemptionManager(
    private val api: Api,
    private val internalConfig: InternalConfig,
    private val logger: Logger,
    private val identifyUser: (userId: String) -> Unit,
) {

    private val handler = Handler(Looper.getMainLooper())

    /**
     * Parses [uri] (expected: `https://screens.qonversion.io/r/{project_uid}/{token}`),
     * POSTs `/v4/web/redeem`, and invokes [callback] on the main thread with the
     * terminal [RedemptionResult]. See [RedemptionResult] for case semantics.
     */
    // The post-redeem identify is best-effort: any failure there must not
    // fail the already-successful redeem, so we intentionally catch broadly
    // and only log.
    @Suppress("TooGenericExceptionCaught")
    fun handleRedemptionLink(uri: Uri, callback: QonversionRedemptionCallback) {
        val token = extractToken(uri)
        if (token == null) {
            logger.error("RedemptionManager: malformed Uri '$uri' — no token segment found.")
            deliver(callback, RedemptionResult.InvalidToken)
            return
        }

        val request = RedeemRequest(
            token = token,
            anonUserId = internalConfig.uid.takeIf { it.isNotBlank() },
        )

        api.redeem(request).enqueue {
            onResponse = { response ->
                when {
                    response.isSuccessful -> {
                        val userId = response.body()?.userId
                        if (!userId.isNullOrBlank()) {
                            // Trigger merge: identify the (now anon) device user
                            // with the Qonversion user id returned by redeem.
                            try {
                                identifyUser(userId)
                            } catch (t: Throwable) {
                                logger.error(
                                    "RedemptionManager: identify after redeem threw: $t"
                                )
                            }
                        } else {
                            logger.error(
                                "RedemptionManager: redeem 200 with empty user_id — " +
                                    "skipping identify, host should call checkEntitlements."
                            )
                        }
                        deliver(callback, RedemptionResult.Success)
                    }
                    response.code() == HTTP_CONFLICT -> {
                        // Disambiguate consumed vs other 409 via /redeem/status.
                        resolveConflict(token, callback)
                    }
                    response.code() == HTTP_NOT_FOUND -> {
                        deliver(callback, RedemptionResult.InvalidToken)
                    }
                    response.code() == HTTP_GONE -> {
                        deliver(callback, RedemptionResult.TokenExpired)
                    }
                    else -> {
                        logger.error(
                            "RedemptionManager: redeem unexpected ${response.code()} — " +
                                "treating as NetworkError."
                        )
                        deliver(callback, RedemptionResult.NetworkError)
                    }
                }
            }
            onFailure = { t ->
                logger.error("RedemptionManager: redeem network failure: $t")
                deliver(callback, RedemptionResult.NetworkError)
            }
        }
    }

    /**
     * POSTs `/v4/web/redeem/reissue` for [email]. Maps HTTP status to one of
     * the [ReissueResult] cases used by the dialog UI to render the right hint.
     */
    fun requestReissue(email: String, callback: (ReissueResult) -> Unit) {
        api.redeemReissue(RedeemReissueRequest(email)).enqueue {
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

    private fun resolveConflict(token: String, callback: QonversionRedemptionCallback) {
        api.redeemStatus(RedeemStatusRequest(token)).enqueue {
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

        val segments = uri.pathSegments ?: return null
        // Expected: /r/{project_uid}/{token}
        if (segments.size < REDEEM_PATH_MIN_SEGMENTS) return null
        if (segments[0] != REDEEM_PATH_PREFIX) return null

        val token = segments.getOrNull(REDEEM_TOKEN_SEGMENT_INDEX)?.trim()
        return token?.takeIf { it.isNotBlank() }
    }

    private fun deliver(callback: QonversionRedemptionCallback, result: RedemptionResult) {
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
    }
}
