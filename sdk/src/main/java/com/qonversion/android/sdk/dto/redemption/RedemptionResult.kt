package com.qonversion.android.sdk.dto.redemption

/**
 * Result of attempting to redeem a Web2App token via [com.qonversion.android.sdk.Qonversion.handleRedemptionLink].
 *
 * The set of cases mirrors the iOS SDK to keep cross-platform parity.
 */
enum class RedemptionResult {
    /**
     * Token was consumed and the entitlement was granted to the current user.
     * The SDK has already called `identify(userId)` for the merge flow, so a
     * subsequent `checkEntitlements` will reflect the new state.
     */
    Success,

    /**
     * The redemption token has expired (its TTL elapsed before the user opened
     * the email link). Hosts should prompt the user to request a new email via
     * [com.qonversion.android.sdk.Qonversion.presentReissueUI].
     */
    TokenExpired,

    /**
     * The token has already been consumed on a previous device or session.
     * Hosts should typically show a "this link was already used" message and
     * suggest re-issue / contact support. The SDK still attempts identify-flow
     * recovery server-side before returning this case (RT4-W2).
     */
    AlreadyConsumed,

    /**
     * The token is not recognized by the server (never existed or was revoked).
     */
    InvalidToken,

    /**
     * Could not reach the Qonversion backend. The host should retry later.
     */
    NetworkError,
}
