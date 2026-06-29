package com.qonversion.android.sdk.internal.redemption

import android.net.Uri
import android.os.Looper
import com.qonversion.android.sdk.dto.redemption.RedemptionResult
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.api.Api
import com.qonversion.android.sdk.internal.dto.redemption.RedeemResponse
import com.qonversion.android.sdk.internal.dto.redemption.RedeemStatusResponse
import com.qonversion.android.sdk.internal.dto.request.RedeemRequest
import com.qonversion.android.sdk.internal.dto.request.RedeemStatusRequest
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.listeners.QonversionRedemptionCallback
import com.squareup.moshi.Moshi
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import okhttp3.MediaType
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Unit tests for [RedemptionManager].
 *
 * Uses Robolectric to get a real [Uri] parser and a controllable main looper.
 * Retrofit [Call]s are hand-mocked because the SDK doesn't ship a
 * `MockWebServer` test fixture and the surface area is small enough that
 * inline mocks are clearer than spinning one up.
 */
@RunWith(RobolectricTestRunner::class)
internal class RedemptionManagerTest {

    private lateinit var api: Api
    private lateinit var internalConfig: InternalConfig
    private lateinit var logger: Logger

    /**
     * Counts how many times the SDK triggered an entitlements refresh after a
     * redeem. Under the grant-first contract the server has already granted the
     * entitlement, so the SDK must NOT identify/merge — it only refreshes the
     * device's entitlement state so the grant is reflected locally.
     */
    private var refreshCount: Int = 0

    private lateinit var manager: RedemptionManager

    @Before
    fun setUp() {
        clearAllMocks()
        api = mockk(relaxed = true)
        internalConfig = mockk(relaxed = true)
        logger = mockk(relaxed = true)
        refreshCount = 0
        every { internalConfig.uid } returns "QON_anon_123"
        manager = RedemptionManager(
            api = api,
            internalConfig = internalConfig,
            logger = logger,
            refreshEntitlements = { refreshCount++ },
        )
    }

    @Test
    fun `handleRedemptionLink parses token from valid Uri and posts request`() {
        val uri = Uri.parse("https://screens.qonversion.io/r/proj_abc/tok_xyz")
        val captured = slot<RedeemRequest>()
        every { api.redeem(capture(captured)) } returns
            alwaysSuccessCall(RedeemResponse(redeemed = true, appUid = "QON_anon_123"))

        val callback = RecordingCallback()
        manager.handleRedemptionLink(uri, callback)

        flushMainLooper()

        assertEquals("tok_xyz", captured.captured.token)
        assertEquals("QON_anon_123", captured.captured.appUid)
        assertEquals("transfer", captured.captured.restoreBehavior)
        assertEquals(RedemptionResult.Success, callback.received)
        assertEquals(1, refreshCount)
    }

    @Test
    fun `redeem request body serializes to canonical contract (token, app_uid, restore_behavior)`() {
        // Golden contract test: pins the OUTGOING JSON keys so a regression to
        // the old "anon_user_id" name (api-gateway/purchaseman read "app_uid")
        // turns this red. Uses the real Moshi adapter the SDK ships with.
        val moshi = Moshi.Builder().build()
        val json = moshi.adapter(RedeemRequest::class.java).toJson(
            RedeemRequest(token = "tok_xyz", appUid = "QON_anon_123"),
        )

        assertTrue("must send token", json.contains("\"token\":\"tok_xyz\""))
        assertTrue("must send app_uid", json.contains("\"app_uid\":\"QON_anon_123\""))
        assertTrue(
            "must send restore_behavior",
            json.contains("\"restore_behavior\":\"transfer\""),
        )
        // Regression guard: the old field name must be gone.
        assertFalse("anon_user_id must NOT be sent", json.contains("anon_user_id"))
    }

    @Test
    fun `redeem response parses canonical contract (redeemed, app_uid) and has no user_id`() {
        // Golden contract test: pins the INCOMING JSON shape {redeemed, app_uid}.
        // A regression that re-adds user_id parsing would not satisfy this.
        val moshi = Moshi.Builder().build()
        val parsed = moshi.adapter(RedeemResponse::class.java)
            .fromJson("{\"redeemed\":true,\"app_uid\":\"QON_anon_123\"}")

        assertEquals(true, parsed?.redeemed)
        assertEquals("QON_anon_123", parsed?.appUid)
    }

    @Test
    fun `handleRedemptionLink rejects qonversion custom-scheme Uri without network call (RT2-W3)`() {
        // Spec rule RT2-W3: only the verified https App Link is allowed as an
        // email-link transport on Android. Any installed app can claim a
        // `qonversion://` intent-filter and hijack the token, so the SDK MUST
        // reject the custom scheme before any network call. The custom scheme
        // is reserved for in-process host→SDK forwarding (see Qonversion.kt
        // docstring on handleRedemptionLink) and is not exercised by this
        // public entry point.
        val uri = Uri.parse("qonversion://screens.qonversion.io/r/proj_abc/tok_xyz")

        val callback = RecordingCallback()
        manager.handleRedemptionLink(uri, callback)

        flushMainLooper()

        assertEquals(RedemptionResult.InvalidToken, callback.received)
        // Load-bearing security assertion: the token must NEVER leak over the
        // wire when the scheme is rejected.
        verify(exactly = 0) { api.redeem(any()) }
        assertEquals(0, refreshCount)
    }

    @Test
    fun `handleRedemptionLink rejects foreign https host without network call (RT2-W3 host)`() {
        // Security: scheme==https alone is not enough. An attacker-controlled
        // host (`https://attacker.com/r/proj/tok`) would otherwise leak the
        // redemption token to a third-party backend. The SDK MUST reject any
        // host other than the canonical App Link host before any network call.
        val uri = Uri.parse("https://attacker.com/r/proj_abc/tok_xyz")

        val callback = RecordingCallback()
        manager.handleRedemptionLink(uri, callback)

        flushMainLooper()

        assertEquals(RedemptionResult.InvalidToken, callback.received)
        // Load-bearing security assertion: the token must NEVER leak over the
        // wire when the host is not the canonical App Link host.
        verify(exactly = 0) { api.redeem(any()) }
        assertEquals(0, refreshCount)
    }

    @Test
    fun `handleRedemptionLink accepts canonical host case-insensitively`() {
        val uri = Uri.parse("https://SCREENS.QONVERSION.IO/r/proj/tok_case")
        every { api.redeem(any()) } returns alwaysSuccessCall(RedeemResponse(redeemed = true, appUid = "QON_anon_123"))

        val callback = RecordingCallback()
        manager.handleRedemptionLink(uri, callback)

        flushMainLooper()

        assertEquals(RedemptionResult.Success, callback.received)
        verify(exactly = 1) { api.redeem(any()) }
    }

    @Test
    fun `handleRedemptionLink guards against in-flight double redemption (no second request)`() {
        val uri = Uri.parse("https://screens.qonversion.io/r/proj/tok_race")
        // A call that never invokes its callback — keeps the redemption
        // in-flight so the second tap collides with the first.
        val pending = pendingCall<RedeemResponse>()
        every { api.redeem(any()) } returns pending

        manager.handleRedemptionLink(uri, RecordingCallback())
        // Second tap (e.g. onCreate + onNewIntent, or a fast double tap) for the
        // same token while the first POST is still in flight.
        manager.handleRedemptionLink(uri, RecordingCallback())

        flushMainLooper()

        // Only ONE network request must have been issued for the same token.
        verify(exactly = 1) { api.redeem(any()) }
    }

    @Test
    fun `handleRedemptionLink allows a fresh redemption after the previous one terminates`() {
        val uri = Uri.parse("https://screens.qonversion.io/r/proj/tok_again")
        every { api.redeem(any()) } returns alwaysSuccessCall(RedeemResponse(redeemed = true, appUid = "QON_anon_123"))

        manager.handleRedemptionLink(uri, RecordingCallback())
        flushMainLooper()
        // First redemption terminated (Success delivered) — guard must be reset.
        manager.handleRedemptionLink(uri, RecordingCallback())
        flushMainLooper()

        verify(exactly = 2) { api.redeem(any()) }
    }

    @Test
    fun `handleRedemptionLink returns InvalidToken when Uri is malformed`() {
        val uri = Uri.parse("https://screens.qonversion.io/some/other/path")

        val callback = RecordingCallback()
        manager.handleRedemptionLink(uri, callback)

        flushMainLooper()

        assertEquals(RedemptionResult.InvalidToken, callback.received)
        // Should never have hit the network for a malformed Uri.
        verify(exactly = 0) { api.redeem(any()) }
        // No identify should have been triggered either.
        assertEquals(0, refreshCount)
    }

    @Test
    fun `handleRedemptionLink returns InvalidToken when path is just the prefix`() {
        val uri = Uri.parse("https://screens.qonversion.io/r/proj_abc")

        val callback = RecordingCallback()
        manager.handleRedemptionLink(uri, callback)

        flushMainLooper()

        assertEquals(RedemptionResult.InvalidToken, callback.received)
        verify(exactly = 0) { api.redeem(any()) }
    }

    @Test
    fun `handleRedemptionLink maps 200 to Success and triggers entitlements refresh`() {
        val uri = Uri.parse("https://screens.qonversion.io/r/proj/tok_ok")
        every { api.redeem(any()) } returns alwaysSuccessCall(RedeemResponse(redeemed = true, appUid = "QON_anon_123"))

        val callback = RecordingCallback()
        manager.handleRedemptionLink(uri, callback)

        flushMainLooper()

        assertEquals(RedemptionResult.Success, callback.received)
        assertEquals(1, refreshCount)
    }

    @Test
    fun `handleRedemptionLink maps 409 with consumed=true to AlreadyConsumed`() {
        val uri = Uri.parse("https://screens.qonversion.io/r/proj/tok_used")
        every { api.redeem(any()) } returns alwaysErrorCall(409)
        val statusCaptured = slot<RedeemStatusRequest>()
        every { api.redeemStatus(capture(statusCaptured)) } returns
            alwaysSuccessCall(RedeemStatusResponse(consumed = true, expired = false))

        val callback = RecordingCallback()
        manager.handleRedemptionLink(uri, callback)

        flushMainLooper()

        assertEquals("tok_used", statusCaptured.captured.token)
        assertEquals(RedemptionResult.AlreadyConsumed, callback.received)
        // No entitlements refresh on AlreadyConsumed — nothing was granted on
        // this device, the host is in recovery flow.
        assertEquals(0, refreshCount)
    }

    @Test
    fun `handleRedemptionLink maps 404 to InvalidToken`() {
        val uri = Uri.parse("https://screens.qonversion.io/r/proj/tok_404")
        every { api.redeem(any()) } returns alwaysErrorCall(404)

        val callback = RecordingCallback()
        manager.handleRedemptionLink(uri, callback)

        flushMainLooper()

        assertEquals(RedemptionResult.InvalidToken, callback.received)
    }

    @Test
    fun `handleRedemptionLink maps 410 to TokenExpired`() {
        val uri = Uri.parse("https://screens.qonversion.io/r/proj/tok_expired")
        every { api.redeem(any()) } returns alwaysErrorCall(410)

        val callback = RecordingCallback()
        manager.handleRedemptionLink(uri, callback)

        flushMainLooper()

        assertEquals(RedemptionResult.TokenExpired, callback.received)
    }

    @Test
    fun `handleRedemptionLink maps 429 rate limit to Retryable (not NetworkError)`() {
        // #1 parity with iOS: a live 429 means the server is reachable and asked
        // the client to back off — surfacing NetworkError would show a wrong
        // "no internet" UX. Must be Retryable.
        val uri = Uri.parse("https://screens.qonversion.io/r/proj/tok_429")
        every { api.redeem(any()) } returns alwaysErrorCall(429)

        val callback = RecordingCallback()
        manager.handleRedemptionLink(uri, callback)

        flushMainLooper()

        assertEquals(RedemptionResult.Retryable, callback.received)
    }

    @Test
    fun `handleRedemptionLink maps 503 server error to Retryable (not NetworkError)`() {
        val uri = Uri.parse("https://screens.qonversion.io/r/proj/tok_503")
        every { api.redeem(any()) } returns alwaysErrorCall(503)

        val callback = RecordingCallback()
        manager.handleRedemptionLink(uri, callback)

        flushMainLooper()

        assertEquals(RedemptionResult.Retryable, callback.received)
    }

    @Test
    fun `handleRedemptionLink maps 401 auth error to Retryable (not NetworkError)`() {
        val uri = Uri.parse("https://screens.qonversion.io/r/proj/tok_401")
        every { api.redeem(any()) } returns alwaysErrorCall(401)

        val callback = RecordingCallback()
        manager.handleRedemptionLink(uri, callback)

        flushMainLooper()

        assertEquals(RedemptionResult.Retryable, callback.received)
    }

    @Test
    fun `handleRedemptionLink with blank app_uid returns Retryable WITHOUT any network call`() {
        // #2 fail-fast (parity with iOS): redeem tokens are single-use. Firing a
        // redeem with no app_uid would burn the token server-side with no user
        // to attach the grant to. When the SDK has no usable app_uid yet, surface
        // Retryable and issue NO network request.
        every { internalConfig.uid } returns ""
        val uri = Uri.parse("https://screens.qonversion.io/r/proj/tok_nouid")

        val callback = RecordingCallback()
        manager.handleRedemptionLink(uri, callback)

        flushMainLooper()

        assertEquals(RedemptionResult.Retryable, callback.received)
        verify(exactly = 0) { api.redeem(any()) }
        assertEquals(0, refreshCount)
    }

    @Test
    fun `handleRedemptionLink delivers Retryable to a second link while one is in flight`() {
        // #6 no eternal spinner: the in-flight guard must not swallow the second
        // legitimate link — its callback MUST fire (Retryable) instead of never
        // being invoked, while the first redemption keeps running unaffected.
        val uri = Uri.parse("https://screens.qonversion.io/r/proj/tok_busy")
        val pending = pendingCall<RedeemResponse>()
        every { api.redeem(any()) } returns pending

        val first = RecordingCallback()
        val second = RecordingCallback()
        manager.handleRedemptionLink(uri, first)
        manager.handleRedemptionLink(uri, second)

        flushMainLooper()

        // First is still in flight (never completed); second must NOT hang.
        assertNull(first.received)
        assertEquals(RedemptionResult.Retryable, second.received)
        // Still only one network request — the guard held.
        verify(exactly = 1) { api.redeem(any()) }
    }

    @Test
    fun `handleRedemptionLink maps network failure to NetworkError`() {
        val uri = Uri.parse("https://screens.qonversion.io/r/proj/tok_neterr")
        every { api.redeem(any()) } returns alwaysNetworkFailureCall()

        val callback = RecordingCallback()
        manager.handleRedemptionLink(uri, callback)

        flushMainLooper()

        assertEquals(RedemptionResult.NetworkError, callback.received)
    }

    /**
     * RT5-N2 contract test (grant-first): a successful redemption MUST NOT
     * identify/merge a client-supplied user id — under grant-first the server
     * has already attached the entitlement to `app_uid`. Instead the SDK must
     * trigger exactly one entitlements refresh so the server grant is reflected
     * on the device.
     *
     * We assert the contract at the [RedemptionManager] boundary (its
     * `refreshEntitlements` lambda is the seam where it hands off to
     * `QProductCenterManager.launch(ActualizePermissions)`). A full end-to-end
     * test asserting that the refresh triggers a real network launch / permissions
     * fetch should live next to the product center manager and exercise the real
     * `QProductCenterManager`; adding that integration-style test requires staging
     * the full launch state machine and is tracked separately (DEV-847 follow-up).
     */
    @Test
    fun `RT5-N2 contract — success path triggers entitlements refresh exactly once and never identifies`() {
        val uri = Uri.parse("https://screens.qonversion.io/r/proj/tok_contract")
        every { api.redeem(any()) } returns
            alwaysSuccessCall(RedeemResponse(redeemed = true, appUid = "QON_anon_123"))

        manager.handleRedemptionLink(uri, RecordingCallback())
        flushMainLooper()

        assertEquals(1, refreshCount)
    }

    // --- Helpers ---------------------------------------------------------

    private class RecordingCallback : QonversionRedemptionCallback {
        var received: RedemptionResult? = null
        override fun onResult(result: RedemptionResult) {
            assertNull("callback invoked more than once", received)
            received = result
        }
    }

    private fun <T> alwaysSuccessCall(body: T): Call<T> {
        val call = mockk<Call<T>>(relaxed = true)
        every { call.enqueue(any()) } answers {
            val cb = arg<Callback<T>>(0)
            cb.onResponse(call, Response.success(body))
        }
        return call
    }

    private fun <T> alwaysErrorCall(code: Int): Call<T> {
        val call = mockk<Call<T>>(relaxed = true)
        every { call.enqueue(any()) } answers {
            val cb = arg<Callback<T>>(0)
            val errBody = ResponseBody.create(MediaType.parse("application/json"), "{}")
            val resp: Response<T> = Response.error(code, errBody)
            cb.onResponse(call, resp)
        }
        return call
    }

    /** A call whose [Call.enqueue] never invokes the callback — stays in-flight. */
    private fun <T> pendingCall(): Call<T> {
        val call = mockk<Call<T>>(relaxed = true)
        every { call.enqueue(any()) } answers { /* never completes */ }
        return call
    }

    private fun <T> alwaysNetworkFailureCall(): Call<T> {
        val call = mockk<Call<T>>(relaxed = true)
        every { call.enqueue(any()) } answers {
            val cb = arg<Callback<T>>(0)
            cb.onFailure(call, RuntimeException("simulated network failure"))
        }
        return call
    }

    private fun flushMainLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }
}
