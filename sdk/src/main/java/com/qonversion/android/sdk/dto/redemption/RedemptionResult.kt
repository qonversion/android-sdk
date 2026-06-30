package com.qonversion.android.sdk.dto.redemption

/**
 * Result of attempting to redeem a Web2App token via [com.qonversion.android.sdk.Qonversion.handleRedemptionLink].
 *
 * The set of cases (6) mirrors the iOS SDK `QONRedemptionResult` to keep
 * cross-platform parity: Success, TokenExpired, AlreadyConsumed, InvalidToken,
 * NetworkError, Retryable. Under the grant-first contract a [Success] means the
 * server already attached the entitlement to `app_uid`; the SDK only refreshes
 * entitlements (it does NOT identify/merge any client user id).
 */
enum class RedemptionResult {
    /**
     * Token was consumed and the entitlement was granted to the current user
     * (server-side, grant-first). The SDK has already triggered an entitlements
     * refresh, so a subsequent `checkEntitlements` will reflect the new state.
     */
    Success,

    /**
     * The redemption token has expired (its TTL elapsed before the user opened
     * the email link). Hosts should prompt the user to request a new email via
     * [com.qonversion.android.sdk.Qonversion.reissueRedemption].
     */
    TokenExpired,

    /**
     * The token has already been consumed on a previous device or session.
     * Hosts should typically show a "this link was already used" message and
     * suggest re-issue / contact support.
     */
    AlreadyConsumed,

    /**
     * The token is not recognized by the server (never existed or was revoked).
     */
    InvalidToken,

    /**
     * Could not reach the Qonversion backend (DNS / TCP / TLS / timeout) — the
     * device genuinely failed to talk to the server. This is NOT used for live
     * server responses such as 429/5xx (see [Retryable]).
     */
    NetworkError,

    /**
     * The backend was reachable but returned a transient/server-side outcome
     * that the host may safely retry later: rate limiting (429), server errors
     * (5xx), or auth/config errors (other non-mapped 4xx such as 401/403). Also
     * surfaced for SDK-side preconditions that can be retried (e.g. the SDK does
     * not yet have a usable `app_uid`, or a redemption is already in flight).
     * Distinguishing this from [NetworkError] avoids a misleading "no internet"
     * UX when the network is live and the server simply asked the client to
     * back off.
     */
    Retryable,
}
