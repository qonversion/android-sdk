package com.qonversion.android.sdk.internal

import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.collect.Maps
import com.qonversion.android.sdk.QonversionConfig
import com.qonversion.android.sdk.dto.QAttributionProvider
import com.qonversion.android.sdk.dto.entitlements.QEntitlementSource
import com.qonversion.android.sdk.dto.QLaunchMode
import com.qonversion.android.sdk.dto.properties.QUserPropertyKey
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.QonversionErrorCode
import com.qonversion.android.sdk.dto.eligibility.QEligibility
import com.qonversion.android.sdk.dto.entitlements.QEntitlementGrantType
import com.qonversion.android.sdk.dto.offerings.QOffering
import com.qonversion.android.sdk.dto.offerings.QOfferingTag
import com.qonversion.android.sdk.dto.offerings.QOfferings
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.internal.di.QDependencyInjector
import com.qonversion.android.sdk.internal.dto.QLaunchResult
import com.qonversion.android.sdk.internal.dto.QPermission
import com.qonversion.android.sdk.internal.dto.QProductRenewState
import com.qonversion.android.sdk.internal.dto.purchase.History
import com.qonversion.android.sdk.internal.dto.request.data.InitRequestData
import com.qonversion.android.sdk.internal.provider.AppStateProvider
import com.qonversion.android.sdk.internal.purchase.Purchase
import com.qonversion.android.sdk.internal.repository.DefaultRepository
import com.qonversion.android.sdk.listeners.QonversionEligibilityCallback
import com.qonversion.android.sdk.listeners.QonversionLaunchCallback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date
import java.util.concurrent.CountDownLatch

private const val PROJECT_KEY = "V4pK6FQo3PiDPj_2vYO1qZpNBbFXNP-a"
private val UID_PREFIX = "QON_test_uid_outager_android_" + System.currentTimeMillis()

@RunWith(AndroidJUnit4::class)
internal class OutagerIntegrationTest {

    private val appStateProvider = object : AppStateProvider {
        override val appState: AppState
            get() = AppState.Foreground
    }

    private val installDate = 1679652674L

    private val noCodeScreenId = "lsarjYcU"

    private val monthlyProduct = QProduct(
        "test_monthly",
        "google_monthly",
        null,
    )
    private val annualProduct = QProduct("test_annual", "google_annual", null)
    private val inappProduct = QProduct("test_inapp", "google_inapp", null)
    private val expectedProducts = mapOf(
        monthlyProduct.qonversionID to monthlyProduct,
        annualProduct.qonversionID to annualProduct,
        inappProduct.qonversionID to inappProduct
    )

    private val expectedOffering = QOffering(
        "main",
        QOfferingTag.Main,
        listOf(annualProduct, monthlyProduct)
    )

    private val expectedOfferings = QOfferings(
        expectedOffering,
        listOf(expectedOffering)
    )

    private val expectedProductPermissions = mapOf(
        "test_monthly" to listOf("premium"),
        "test_annual" to listOf("premium"),
        "test_inapp" to listOf("noAds")
    )

    private val purchase = Purchase(
        storeProductId = "google_monthly",
        orderId = "GPA.3307-0767-0668-99058",
        originalOrderId = "GPA.3307-0767-0668-99058",
        purchaseTime = 1679933171,
        purchaseToken = "lgeigljfpmeoddkcebkcepjc.AO-J1Oy305qZj99jXTPEVBN8UZGoYAtjDLj4uTjRQvUFaG0vie-nr6VBlN0qnNDMU8eJR-sI7o3CwQyMOEHKl8eJsoQ86KSFzxKBR07PSpHLI_o7agXhNKY",
    )

    @Test
    fun init() {
        // given
        val signal = CountDownLatch(1)

        val uid = UID_PREFIX + "_init"
        val data = InitRequestData(
            installDate,
            null,
            emptyList(),
            object : QonversionLaunchCallback {
                override fun onSuccess(launchResult: QLaunchResult) {
                    // then
                    assertEquals(launchResult.uid, uid)
                    assertTrue(
                        Maps.difference(expectedProducts, launchResult.products).areEqual()
                    )
                    assertTrue(
                        Maps.difference(emptyMap(), launchResult.permissions).areEqual()
                    )
                    assertEquals(expectedOfferings, launchResult.offerings)
                    assertTrue(
                        Maps.difference(
                            expectedProductPermissions,
                            launchResult.productPermissions!!
                        ).areEqual()
                    )
                    signal.countDown()
                }

                override fun onError(error: QonversionError, httpCode: Int?) {
                    fail("Shouldn't fail")
                }
            }
        )

        val repository = initRepository(uid)

        // when
        repository.init(data)

        signal.await()
    }

    @Test
    fun purchase() {
        // given
        val signal = CountDownLatch(1)

        val expectedPermissions = mapOf(
            "premium" to QPermission(
                "premium",
                "test_monthly",
                QProductRenewState.Unknown,
                Date(1679933171000),
                Date(1680537971000), // plus week, as we don't send duration
                QEntitlementSource.Unknown,
                1,
                0,
                null,
                null,
                null,
                null,
                QEntitlementGrantType.Purchase,
                null,
                emptyList()
            )
        )

        val uid = UID_PREFIX + "_purchase"
        val callback = object : QonversionLaunchCallback {
            override fun onSuccess(launchResult: QLaunchResult) {
                // then
                assertEquals(launchResult.uid, uid)
                assertTrue(
                    Maps.difference(expectedProducts, launchResult.products).areEqual()
                )
                assertTrue(Maps.difference(expectedPermissions, launchResult.permissions).areEqual())
                assertEquals(expectedOfferings, launchResult.offerings)
                assertTrue(
                    Maps.difference(
                        expectedProductPermissions,
                        launchResult.productPermissions!!
                    ).areEqual()
                )
                signal.countDown()
            }

            override fun onError(error: QonversionError, httpCode: Int?) {
                fail("Shouldn't fail")
            }
        }

        val repository = initRepository(uid)

        // when
        withNewUserCreated(repository) { error ->
            error?.let {
                fail("Failed to create user")
            }

            repository.purchase(
                installDate,
                purchase,
                "test_monthly",
                callback
            )
        }

        signal.await()
    }

    @Test
    fun restore() {
        // given
        val signal = CountDownLatch(1)

        val history = listOf(
            History(
                "google_monthly",
                "lgeigljfpmeoddkcebkcepjc.AO-J1Oy305qZj99jXTPEVBN8UZGoYAtjDLj4uTjRQvUFaG0vie-nr6VBlN0qnNDMU8eJR-sI7o3CwQyMOEHKl8eJsoQ86KSFzxKBR07PSpHLI_o7agXhNKY",
                1679933171,
            )
        )
        val expectedPermissions = mapOf(
            "premium" to QPermission(
                "premium",
                "test_monthly",
                QProductRenewState.Unknown,
                Date(1679933171000),
                Date(1680537971000), // plus seven days
                QEntitlementSource.Unknown,
                1,
                0,
                null,
                null,
                null,
                null,
                QEntitlementGrantType.Purchase,
                null,
                emptyList()
            )
        )

        val uid = UID_PREFIX + "_restore"
        val callback = object : QonversionLaunchCallback {
            override fun onSuccess(launchResult: QLaunchResult) {
                // then
                assertEquals(launchResult.uid, uid)
                assertTrue(
                    Maps.difference(expectedProducts, launchResult.products).areEqual()
                )
                assertTrue(
                    Maps.difference(expectedPermissions, launchResult.permissions).areEqual()
                )
                assertEquals(expectedOfferings, launchResult.offerings)
                assertTrue(
                    Maps.difference(
                        expectedProductPermissions,
                        launchResult.productPermissions!!
                    ).areEqual()
                )
                signal.countDown()
            }

            override fun onError(error: QonversionError, httpCode: Int?) {
                fail("Shouldn't fail")
            }
        }

        val repository = initRepository(uid)

        // when
        withNewUserCreated(repository) { error ->
            error?.let {
                fail("Failed to create user")
            }

            repository.restoreRequest(installDate, history, callback)
        }

        signal.await()
    }

    @Test
    fun attribution() {
        // given
        val signal = CountDownLatch(1)
        val testAttributionInfo = mapOf(
            "one" to 3,
            "to be or not" to "be",
            "toma" to "s"
        )

        val uid = UID_PREFIX + "_attribution"
        val repository = initRepository(uid)

        // when and then
        withNewUserCreated(repository) { error ->
            error?.let {
                fail("Failed to create user")
            }

            repository.attribution(
                testAttributionInfo,
                QAttributionProvider.AppsFlyer.id,
                { signal.countDown() },
                { fail("Shouldn't fail") }
            )
        }

        signal.await()
    }

    @Test
    fun sendProperties() {
        // given
        val signal = CountDownLatch(1)
        val testProperties = mapOf(
            "customProperty" to "custom property value",
            QUserPropertyKey.CustomUserId.userPropertyCode to "custom user id"
        )

        val uid = UID_PREFIX + "_sendProperties"
        val repository = initRepository(uid)

        // when and then
        withNewUserCreated(repository) { error ->
            error?.let {
                fail("Failed to create user")
            }

            repository.sendProperties(
                testProperties,
                { signal.countDown() },
                { fail("Shouldn't fail") }
            )
        }

        signal.await()
    }

    @Test
    fun getProperties() {
        // given
        val signal = CountDownLatch(1)

        val uid = UID_PREFIX + "_sendProperties"
        val repository = initRepository(uid)

        // when and then
        repository.getProperties(
            { fail("Shouldn't succeed") },
            { error ->
                assertEquals(error.code, QonversionErrorCode.BackendError)
                assertTrue("HTTP status code=503, error=Service Unavailable. " == error.additionalMessage)
                signal.countDown()
            }
        )

        signal.await()
    }

    @Test
    fun eligibilityForProductIds() {
        // given
        val signal = CountDownLatch(1)
        val productIds = listOf(monthlyProduct.qonversionID, annualProduct.qonversionID)

        val callback = object : QonversionEligibilityCallback {
            override fun onSuccess(eligibilities: Map<String, QEligibility>) {
                fail("Shouldn't succeed")
            }

            override fun onError(error: QonversionError) {
                // Unsupported method on Outager
                assertEquals(error.code, QonversionErrorCode.BackendError)
                assertEquals(error.additionalMessage, """HTTP status code=503, data={"message":"Service Unavailable","code":0,"status":503}. """)
                signal.countDown()
            }
        }

        val uid = UID_PREFIX + "_eligibilityForProductIds"
        val repository = initRepository(uid)

        // when and then
        withNewUserCreated(repository) { error ->
            error?.let {
                fail("Failed to create user")
            }

            repository.eligibilityForProductIds(
                productIds,
                installDate,
                callback
            )
        }

        signal.await()
    }

    @Test
    fun identify() {
        // given
        val signal = CountDownLatch(1)
        val uid = UID_PREFIX + "_identify"
        val identityId = "identity_for_$uid"

        val repository = initRepository(uid)

        // when and then
        withNewUserCreated(repository) { error ->
            error?.let {
                fail("Failed to create user")
            }

            repository.identify(
                identityId,
                uid,
                { newAnonId ->
                    assertEquals(newAnonId, uid)

                    signal.countDown()
                },
                { fail("Shouldn't fail") }
            )
        }

        signal.await()
    }

    @Test
    fun sendPushToken() {
        // given
        val signal = CountDownLatch(1)

        val token = "dt70kovLQdKymNnhIY6I94:APA91bGfg6m108VFio2ZdgLR6U0B2PtqAn0hIPVU7M4jKklkMxqDUrjoThpX_K60M7CfH8IVZqtku31ei2hmjdJZDfm-bdAl7uxLDWFU8yVcA6-3wBMn3nsYmUrhYWom-qgGC7yIUYzR"

        val uid = UID_PREFIX + "_sendPushToken"
        val repository = initRepository(uid)

        // when
        withNewUserCreated(repository) { error ->
            error?.let {
                fail("Failed to create user")
            }

            repository.sendPushToken(token)
        }

        // then
        // check that nothing critical happens
        Handler(Looper.getMainLooper()).postDelayed(
            { signal.countDown() },
            1000
        )
        signal.await()
    }

    @Test
    fun screens() {
        // given
        val signal = CountDownLatch(1)

        val uid = UID_PREFIX + "_screens"
        val repository = initRepository(uid)

        // when
        withNewUserCreated(repository) { initError ->
            initError?.let {
                fail("Failed to create user")
            }

            repository.screens(
                noCodeScreenId,
                { fail("Shouldn't succeed") },
                { error ->
                    // Unsupported method on Outager
                    assertEquals(error.code, QonversionErrorCode.BackendError)
                    assertEquals(error.additionalMessage, """HTTP status code=503, error=Service Unavailable. """)
                    signal.countDown()
                }
            )
        }

        signal.await()
    }

    @Test
    fun views() {
        // given
        val signal = CountDownLatch(1)

        val uid = UID_PREFIX + "_views"
        val repository = initRepository(uid)

        // when
        withNewUserCreated(repository) { error ->
            error?.let {
                fail("Failed to create user")
            }

            repository.views(noCodeScreenId)
        }

        // then
        // check that nothing critical happens
        Handler(Looper.getMainLooper()).postDelayed(
            { signal.countDown() },
            1000
        )
        signal.await()
    }

    @Test
    fun actionPoints() {
        // given
        val signal = CountDownLatch(1)

        val uid = UID_PREFIX + "_actionPoints"
        val repository = initRepository(uid)

        // when
        withNewUserCreated(repository) { initError ->
            initError?.let {
                fail("Failed to create user")
            }

            repository.actionPoints(
                mapOf(
                    "type" to "screen_view",
                    "active" to "1"
                ),
                { fail("Shouldn't succeed") },
                { error ->
                    // Unsupported method on Outager
                    assertEquals(error.code, QonversionErrorCode.BackendError)
                    assertEquals(error.additionalMessage, """HTTP status code=503, error=Service Unavailable. """)
                    signal.countDown()
                }
            )
        }

        signal.await()
    }

    private fun withNewUserCreated(
        repository: DefaultRepository,
        onComplete: (error: QonversionError?) -> Unit
    ) {
        val data = InitRequestData(
            installDate,
            null,
            emptyList(),
            object : QonversionLaunchCallback {
                override fun onSuccess(launchResult: QLaunchResult) {
                    onComplete(null)
                }

                override fun onError(error: QonversionError, httpCode: Int?) {
                    onComplete(error)
                }
            }
        )
        repository.init(data)
    }

    private fun initRepository(uid: String, projectKey: String = PROJECT_KEY): DefaultRepository {
        val qonversionConfig = QonversionConfig.Builder(
            ApplicationProvider.getApplicationContext(),
            projectKey,
            QLaunchMode.SubscriptionManagement
        )
            .setProxyURL("<paste outager link here>")
            .build()
        val internalConfig = InternalConfig(qonversionConfig)
        internalConfig.uid = uid
        QDependencyInjector.buildAppComponent(
            qonversionConfig.application,
            internalConfig,
            appStateProvider
        )

        return QDependencyInjector.appComponent.qonversionRepository()
    }
}
