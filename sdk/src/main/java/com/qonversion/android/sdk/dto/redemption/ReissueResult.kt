package com.qonversion.android.sdk.dto.redemption

/**
 * Result of requesting a new redemption email via
 * [com.qonversion.android.sdk.Qonversion.reissueRedemption].
 *
 * Web2App reissue lets a user ask for a fresh redemption link when the original
 * one is lost or expired (`POST /v4/web/redeem/reissue`). The SDK does NOT
 * collect the email itself — the host app owns that UI and passes the email in,
 * matching the iOS `reissueRedemption(email:completion:)` contract.
 *
 * The cases map the HTTP outcome iOS surfaces via `(success, statusCode)`:
 * 200 → [Sent] (the backend returns 200 for any accepted email with no existence
 * oracle — [Sent] means "accepted", NOT "delivered to a real inbox"); 429 →
 * [RateLimited]; 400 invalid_email (malformed, non-blank) or a blank email caught
 * client-side → [InvalidEmail]; 5xx, transport failure, and other non-user-fixable
 * codes → [ServerError].
 */
enum class ReissueResult {
    /**
     * The reissue request was accepted (HTTP 200). Note this does NOT confirm a
     * matching purchase exists or that the mail was delivered — the backend
     * intentionally returns 200 for any accepted email so it can't be used as an
     * account-existence oracle. Treat as "accepted", not "delivered".
     */
    Sent,

    /**
     * The email is invalid: either blank (rejected client-side with NO network
     * request, parity with iOS's empty-email guard) or malformed and rejected by
     * the backend with 400 invalid_email. User-fixable — hosts should prompt for a
     * valid email rather than offering a blind retry.
     */
    InvalidEmail,

    /**
     * The backend rate-limited the request (HTTP 429). Hosts should ask the user
     * to try again later.
     */
    RateLimited,

    /**
     * The backend returned a server error (5xx) or the device could not reach it
     * (DNS / TCP / TLS / timeout). Hosts should offer a generic retry.
     */
    ServerError,
}
