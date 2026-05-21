package com.qonversion.android.sdk.internal

import android.app.Application
import com.android.billingclient.api.Purchase
import android.os.Build
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.QonversionErrorCode
import com.qonversion.android.sdk.internal.api.RequestTrigger
import com.qonversion.android.sdk.internal.billing.QonversionBillingService
import com.qonversion.android.sdk.internal.dto.QLaunchResult
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.provider.AppStateProvider
import com.qonversion.android.sdk.internal.repository.QRepository
import com.qonversion.android.sdk.internal.services.QUserInfoService
import com.qonversion.android.sdk.internal.storage.LaunchResultCacheWrapper
import com.qonversion.android.sdk.internal.storage.PurchasesCache
import io.mockk.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

/**
 * Web 2 App M1 [RT5-N2] — contract tests for [QProductCenterManager.identify].
 *
 * The /v4/web/redeem/status recovery UX (DEV-845, plan §"POST
 * /v4/web/redeem/status") relies on `Qonversion.identify(userID:)`
 * triggering a fresh entitlement fetch via the merged identity. The
 * SDK currently honours this implicitly: when the new identity differs
 * from the current user id, [QProductCenterManager.processIdentity]
 * calls `launchResultCache.clearPermissionsCache()` followed by
 * `launch(RequestTrigger.Identify)`.
 *
 * If a future SDK change defers or skips the launch step (e.g.,
 * lazy-fetch on next checkEntitlements call), the web→app recovery
 * flow silently breaks — the user signs in but the server-side
 * entitlement never reaches the host app. These tests pin the
 * contract so any regression fails CI.
 *
 * Source of truth — Android: QProductCenterManager.kt:221-257.
 * Symmetric iOS contract test lives in:
 *   qonversion-ios-sdk/Tests/.../QNProductCenterManagerIdentifyContractTests.swift
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1])
internal class QProductCenterManagerIdentifyContractTest {

    private val mockLogger: Logger = mockk(relaxed = true)
    private val mockDeviceStorage = mockk<PurchasesCache>(relaxed = true)
    private val mockHandledPurchasesCache = mockk<QHandledPurchasesCache>(relaxed = true)
    private val mockLaunchResultCacheWrapper = mockk<LaunchResultCacheWrapper>(relaxed = true)
    private val mockContext = mockk<Application>(relaxed = true)
    private val mockRepository = mockk<QRepository>(relaxed = true)
    private val mockUserInfoService = mockk<QUserInfoService>(relaxed = true)
    private val mockIdentityManager = mockk<QIdentityManager>(relaxed = true)
    private val mockBillingService = mockk<QonversionBillingService>(relaxed = true)
    private val mockConfig = mockk<InternalConfig>(relaxed = true)
    private val mockAppStateProvider = mockk<AppStateProvider>(relaxed = true)
    private val mockRemoteConfigManager = mockk<QRemoteConfigManager>(relaxed = true)

    private lateinit var pcm: QProductCenterManager

    @Before
    fun setUp() {
        clearAllMocks()

        // isLaunchingFinished := sessionLaunchResult != null. Set it so
        // identify() takes the synchronous fast path (no init → identify).
        val launchResult = QLaunchResult("uid_initial", Date(), offerings = null)
        every { mockLaunchResultCacheWrapper.sessionLaunchResult } returns launchResult

        // Force kids-mode so launch() skips AdvertisingProvider, which
        // would otherwise spin up a background Thread and break the
        // synchronous verifyOrder window.
        every { mockConfig.primaryConfig.isKidsMode } returns true

        // billingService.queryPurchases is the synchronous entry point
        // into continueLaunchWithPurchasesInfo → processInit →
        // repository.init. With the relaxed mock it's a no-op and
        // repository.init never runs. Invoke the success lambda with an
        // empty list so processInitDefault() fires on the same thread.
        every {
            mockBillingService.queryPurchases(any(), captureLambda())
        } answers {
            lambda<(List<Purchase>) -> Unit>().captured.invoke(emptyList())
        }

        pcm = QProductCenterManager(
            mockContext,
            mockRepository,
            mockLogger,
            mockDeviceStorage,
            mockHandledPurchasesCache,
            mockLaunchResultCacheWrapper,
            mockUserInfoService,
            mockIdentityManager,
            mockConfig,
            mockAppStateProvider,
            mockRemoteConfigManager
        )
        pcm.billingService = mockBillingService
    }

    /**
     * RT5-N2 happy path — different identity successfully linked: the
     * SDK MUST clear the cached permissions AND issue a fresh launch
     * with `RequestTrigger.Identify`. This is the wire that the web→app
     * recovery UX depends on.
     *
     * If this test starts failing because someone moved the
     * clear+launch to a lazy code path, see the [RT5-N2] note in
     * plan §"SDK contract dependency" — the host app integration
     * docs MUST then add an explicit `Qonversion.checkEntitlements()`
     * call after `identify()` returns success.
     */
    @Test
    fun `identify with different uid clears cache and re-launches with Identify trigger`() {
        val newIdentity = "user@example.com"
        val mergedUid = "uid_merged_999"

        every { mockUserInfoService.obtainUserId() } returns "uid_initial"
        // Identity manager succeeds with a DIFFERENT qonversion uid (the
        // cross-user-success branch in processIdentity).
        every {
            mockIdentityManager.identify(eq(newIdentity), any())
        } answers {
            val cb = secondArg<IdentityManagerCallback>()
            cb.onSuccess(mergedUid)
        }

        pcm.identify(newIdentity)

        // Order matters — clear THEN re-launch. If launch reads the
        // cache and finds stale permissions before clear, the UX is
        // broken.
        verifyOrder {
            mockConfig.uid = mergedUid
            mockRemoteConfigManager.onUserUpdate()
            mockLaunchResultCacheWrapper.clearPermissionsCache()
            mockRepository.init(match { it.requestTrigger == RequestTrigger.Identify })
        }
    }

    /**
     * Same-uid case is the "user re-identifies themselves" branch.
     * The contract here is the OPPOSITE: the SDK MUST NOT clear cache
     * or re-launch (would waste a request and could flash UI). Test
     * pins the negative branch so a future patch can't accidentally
     * always-launch.
     */
    @Test
    fun `identify with same uid does NOT clear cache or re-launch`() {
        val newIdentity = "user@example.com"
        val sameUid = "uid_initial"

        every { mockUserInfoService.obtainUserId() } returns sameUid
        every {
            mockIdentityManager.identify(eq(newIdentity), any())
        } answers {
            secondArg<IdentityManagerCallback>().onSuccess(sameUid)
        }

        pcm.identify(newIdentity)

        verify(exactly = 0) { mockLaunchResultCacheWrapper.clearPermissionsCache() }
        verify(exactly = 0) {
            mockRepository.init(match { it.requestTrigger == RequestTrigger.Identify })
        }
    }

    /**
     * Identity error must NOT clear cache or re-launch — silently
     * keeping prior entitlements is the correct fallback. Pins the
     * error branch so future error handlers can't accidentally taint
     * the cache.
     */
    @Test
    fun `identify with identityManager error does NOT touch cache`() {
        val newIdentity = "user@example.com"

        every { mockUserInfoService.obtainUserId() } returns "uid_initial"
        every {
            mockIdentityManager.identify(eq(newIdentity), any())
        } answers {
            secondArg<IdentityManagerCallback>().onError(
                QonversionError(QonversionErrorCode.BackendError)
            )
        }

        pcm.identify(newIdentity)

        verify(exactly = 0) { mockLaunchResultCacheWrapper.clearPermissionsCache() }
        verify(exactly = 0) {
            mockRepository.init(match { it.requestTrigger == RequestTrigger.Identify })
        }
    }
}
