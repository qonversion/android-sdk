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
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import okhttp3.MediaType
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    private lateinit var identifiedUserIds: MutableList<String>

    private lateinit var manager: RedemptionManager

    @Before
    fun setUp() {
        clearAllMocks()
        api = mockk(relaxed = true)
        internalConfig = mockk(relaxed = true)
        logger = mockk(relaxed = true)
        identifiedUserIds = mutableListOf()
        every { internalConfig.uid } returns "QON_anon_123"
        manager = RedemptionManager(
            api = api,
            internalConfig = internalConfig,
            logger = logger,
            identifyUser = { identifiedUserIds.add(it) },
        )
    }

    @Test
    fun `handleRedemptionLink parses token from valid Uri and posts request`() {
        val uri = Uri.parse("https://screens.qonversion.io/r/proj_abc/tok_xyz")
        val captured = slot<RedeemRequest>()
        every { api.redeem(capture(captured)) } returns alwaysSuccessCall(RedeemResponse(userId = "QON_user_42"))

        val callback = RecordingCallback()
        manager.handleRedemptionLink(uri, callback)

        flushMainLooper()

        assertEquals("tok_xyz", captured.captured.token)
        assertEquals("QON_anon_123", captured.captured.anonUserId)
        assertEquals("transfer", captured.captured.restoreBehavior)
        assertEquals(RedemptionResult.Success, callback.received)
        assertEquals(listOf("QON_user_42"), identifiedUserIds)
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
        assertEquals(emptyList<String>(), identifiedUserIds)
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
        assertEquals(emptyList<String>(), identifiedUserIds)
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
    fun `handleRedemptionLink maps 200 to Success and triggers identify`() {
        val uri = Uri.parse("https://screens.qonversion.io/r/proj/tok_ok")
        every { api.redeem(any()) } returns alwaysSuccessCall(RedeemResponse(userId = "QON_user_99"))

        val callback = RecordingCallback()
        manager.handleRedemptionLink(uri, callback)

        flushMainLooper()

        assertEquals(RedemptionResult.Success, callback.received)
        assertEquals(listOf("QON_user_99"), identifiedUserIds)
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
        // identify must NOT be called on AlreadyConsumed — the host is in
        // recovery flow, not merge flow.
        assertEquals(emptyList<String>(), identifiedUserIds)
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
    fun `handleRedemptionLink maps network failure to NetworkError`() {
        val uri = Uri.parse("https://screens.qonversion.io/r/proj/tok_neterr")
        every { api.redeem(any()) } returns alwaysNetworkFailureCall()

        val callback = RecordingCallback()
        manager.handleRedemptionLink(uri, callback)

        flushMainLooper()

        assertEquals(RedemptionResult.NetworkError, callback.received)
    }

    /**
     * RT5-N2 contract test: a successful redemption MUST cause `identify` to
     * be invoked on the product center manager so the SDK runs a new launch
     * and pulls the freshly-granted entitlements.
     *
     * We assert the contract at the [RedemptionManager] boundary (its
     * `identifyUser` lambda is the seam where it hands off to
     * `QProductCenterManager.identify`). A full end-to-end contract test that
     * asserts `identify(newUserId)` triggers a network launch / permissions
     * fetch should live next to the product center manager and exercise the
     * real `QProductCenterManager`. See [com.qonversion.android.sdk.internal
     * .QProductCenterManager.identify] — adding that integration-style test
     * requires staging the full launch state machine and is tracked
     * separately (DEV-847 follow-up).
     */
    @Test
    fun `RT5-N2 contract — success path forwards Qonversion user id to identify exactly once`() {
        val uri = Uri.parse("https://screens.qonversion.io/r/proj/tok_contract")
        every { api.redeem(any()) } returns alwaysSuccessCall(RedeemResponse(userId = "QON_user_contract"))

        manager.handleRedemptionLink(uri, RecordingCallback())
        flushMainLooper()

        assertEquals(listOf("QON_user_contract"), identifiedUserIds)
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
