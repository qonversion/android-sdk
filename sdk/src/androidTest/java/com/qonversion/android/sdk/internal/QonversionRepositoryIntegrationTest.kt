package com.qonversion.android.sdk.internal

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.collect.Maps
import com.qonversion.android.sdk.QonversionConfig
import com.qonversion.android.sdk.dto.QAttributionProvider
import com.qonversion.android.sdk.dto.QEntitlementSource
import com.qonversion.android.sdk.dto.QLaunchMode
import com.qonversion.android.sdk.dto.QUserProperty
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.eligibility.QEligibility
import com.qonversion.android.sdk.dto.eligibility.QIntroEligibilityStatus
import com.qonversion.android.sdk.dto.offerings.QOffering
import com.qonversion.android.sdk.dto.offerings.QOfferingTag
import com.qonversion.android.sdk.dto.offerings.QOfferings
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.dto.products.QProductDuration
import com.qonversion.android.sdk.dto.products.QProductType
import com.qonversion.android.sdk.internal.di.QDependencyInjector
import com.qonversion.android.sdk.internal.dto.QLaunchResult
import com.qonversion.android.sdk.internal.dto.QPermission
import com.qonversion.android.sdk.internal.dto.QProductRenewState
import com.qonversion.android.sdk.internal.dto.purchase.History
import com.qonversion.android.sdk.internal.dto.request.data.InitRequestData
import com.qonversion.android.sdk.internal.provider.AppStateProvider
import com.qonversion.android.sdk.internal.purchase.Purchase
import com.qonversion.android.sdk.listeners.QonversionEligibilityCallback
import com.qonversion.android.sdk.listeners.QonversionLaunchCallback
import junit.framework.TestCase.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date
import java.util.concurrent.CountDownLatch

private val uidPrefix = "QON_test_uid_android_" + System.currentTimeMillis()

@RunWith(AndroidJUnit4::class)
internal class QonversionRepositoryIntegrationTest {

    private val appStateProvider = object : AppStateProvider {
        override val appState: AppState
            get() = AppState.Foreground
    }

    private val installDate = 1679652674L

    private val noCodeScreenId = "lsarjYcU"

    private val monthlyProduct = QProduct(
        "test_monthly",
        "google_monthly",
        QProductType.Subscription,
        QProductDuration.Monthly
    )
    private val annualProduct =
        QProduct("test_annual", "google_annual", QProductType.Trial, QProductDuration.Annual)
    private val inappProduct = QProduct("test_inapp", "google_inapp", QProductType.InApp, null)
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

    private val expectedPermissions = mapOf(
        "premium" to QPermission(
            "premium",
            "test_monthly",
            QProductRenewState.Canceled,
            Date(1679933171000),
            Date(1679935273000),
            QEntitlementSource.PlayStore,
            0
        )
    )

    private val purchase = Purchase(
        detailsToken = "AEuhp4Kd9cZ3ZlkS2MylEXHBcZVLjwwllncPBm4a6lrVvj3uYGICnsE5w87i81qNsa38DPOW08BcZfLxJFxIWeISVwoBkT55tA2Bb6cKGsip724=",
        title = "DONT CHANGE! Sub for integration tests. (Qonversion Sample)",
        description = "",
        productId = "google_monthly",
        type = "subs",
        originalPrice = "$6.99",
        originalPriceAmountMicros = 6990000,
        priceCurrencyCode = "SGD",
        price = "6.99",
        priceAmountMicros = 6990000,
        periodUnit = 2,
        periodUnitsCount = 1,
        freeTrialPeriod = "",
        introductoryAvailable = false,
        introductoryPriceAmountMicros = 0,
        introductoryPrice = "0.00",
        introductoryPriceCycles = 0,
        introductoryPeriodUnit = 0,
        introductoryPeriodUnitsCount = null,
        orderId = "GPA.3307-0767-0668-99058",
        originalOrderId = "GPA.3307-0767-0668-99058",
        packageName = "com.qonversion.sample",
        purchaseTime = 1679933171,
        purchaseState = 1,
        purchaseToken = "lgeigljfpmeoddkcebkcepjc.AO-J1Oy305qZj99jXTPEVBN8UZGoYAtjDLj4uTjRQvUFaG0vie-nr6VBlN0qnNDMU8eJR-sI7o3CwQyMOEHKl8eJsoQ86KSFzxKBR07PSpHLI_o7agXhNKY",
        acknowledged = false,
        autoRenewing = true,
        paymentMode = 0
    )

    @Test
    fun init() {
        // given
        val signal = CountDownLatch(1)

        val data = InitRequestData(
            installDate,
            null,
            emptyList(),
            object : QonversionLaunchCallback {
                override fun onSuccess(launchResult: QLaunchResult) {
                    // then
                    assertTrue(launchResult.uid.isNotEmpty())
                    assertTrue(Maps.difference(expectedProducts, launchResult.products).areEqual())
                    assertTrue(Maps.difference(emptyMap(), launchResult.permissions).areEqual())
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

        val uid = uidPrefix + "_init"
        val repository = initRepositoryForUid(uid)

        // when
        repository.init(data)

        signal.await()
    }

    @Test
    fun purchase() {
        // given
        val signal = CountDownLatch(1)

        val callback = object : QonversionLaunchCallback {
            override fun onSuccess(launchResult: QLaunchResult) {
                // then
                assertTrue(launchResult.uid.isNotEmpty())
                assertTrue(Maps.difference(expectedProducts, launchResult.products).areEqual())
                assertTrue(Maps.difference(emptyMap(), launchResult.permissions).areEqual())
                assertEquals(expectedOfferings, launchResult.offerings)
                assertTrue(
                    Maps.difference(
                        emptyMap(),
                        launchResult.productPermissions!!
                    ).areEqual()
                )
                signal.countDown()
            }

            override fun onError(error: QonversionError, httpCode: Int?) {
                fail("Shouldn't fail")
            }
        }

        val uid = uidPrefix + "_purchase"
        val repository = initRepositoryForUid(uid)

        // when
        withNewUserCreated(repository) { error ->
            error?.let {
                fail("Failed to create user")
            }

            repository.purchase(
                installDate,
                purchase,
                null,
                "test_monthly",
                callback
            )
        }

        signal.await()
    }

    @Test
    fun purchase_for_existing_user() {
        // given
        val signal = CountDownLatch(1)

        val callback = object : QonversionLaunchCallback {
            override fun onSuccess(launchResult: QLaunchResult) {
                // then
                Log.i("LaunchResult", launchResult.toString())
                assertTrue(launchResult.uid.isNotEmpty())
                assertTrue(Maps.difference(expectedProducts, launchResult.products).areEqual())
                assertTrue(Maps.difference(expectedPermissions, launchResult.permissions).areEqual())
                assertEquals(expectedOfferings, launchResult.offerings)
                assertTrue(
                    Maps.difference(
                        emptyMap(),
                        launchResult.productPermissions!!
                    ).areEqual()
                )
                signal.countDown()
            }

            override fun onError(error: QonversionError, httpCode: Int?) {
                fail("Shouldn't fail")
            }
        }

        val uid = "QON_test_uid1679992132407"
        val repository = initRepositoryForUid(uid)

        // when
        repository.purchase(installDate, purchase, null, "test_monthly", callback)

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
                "SGD",
                "6.99"
            )
        )

        val callback = object : QonversionLaunchCallback {
            override fun onSuccess(launchResult: QLaunchResult) {
                // then
                assertTrue(launchResult.uid.isNotEmpty())
                assertTrue(Maps.difference(expectedProducts, launchResult.products).areEqual())
                assertTrue(Maps.difference(expectedPermissions, launchResult.permissions).areEqual())
                assertEquals(expectedOfferings, launchResult.offerings)
                assertTrue(
                    Maps.difference(
                        emptyMap(),
                        launchResult.productPermissions!!
                    ).areEqual()
                )
                signal.countDown()
            }

            override fun onError(error: QonversionError, httpCode: Int?) {
                fail("Shouldn't fail")
            }
        }

        val uid = uidPrefix + "_restore"
        val repository = initRepositoryForUid(uid)

        // when
        withNewUserCreated(repository) { error ->
            error?.let {
                fail("Failed to create user")
            }

            repository.restoreRequest(installDate, history, callback)
        }

        signal.await()

        // when
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

        val uid = uidPrefix + "_attribution"
        val repository = initRepositoryForUid(uid)

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
            QUserProperty.CustomUserId.userPropertyCode to "custom user id"
        )

        val uid = uidPrefix + "_sendProperties"
        val repository = initRepositoryForUid(uid)

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
    fun eligibilityForProductIds() {
        // given
        val signal = CountDownLatch(1)
        val productIds = listOf(monthlyProduct.qonversionID, annualProduct.qonversionID)
        val expectedResult = mapOf(
            monthlyProduct.qonversionID to QEligibility(QIntroEligibilityStatus.NonIntroProduct),
            annualProduct.qonversionID to QEligibility(QIntroEligibilityStatus.Unknown),
            inappProduct.qonversionID to QEligibility(QIntroEligibilityStatus.NonIntroProduct)
        )

        val callback = object : QonversionEligibilityCallback {
            override fun onSuccess(eligibilities: Map<String, QEligibility>) {
                assertTrue(Maps.difference(expectedResult, eligibilities).areEqual())

                signal.countDown()
            }

            override fun onError(error: QonversionError) {
                fail("Shouldn't fail")
            }
        }

        val uid = uidPrefix + "_eligibilityForProductIds"
        val repository = initRepositoryForUid(uid)

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
        val uid = uidPrefix + "_identify"
        val identityId = "identity_for_$uid"

        val repository = initRepositoryForUid(uid)

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

        val uid = uidPrefix + "_sendPushToken"
        val repository = initRepositoryForUid(uid)

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

        val uid = uidPrefix + "_screens"
        val repository = initRepositoryForUid(uid)

        // when
        withNewUserCreated(repository) { error ->
            error?.let {
                fail("Failed to create user")
            }

            repository.screens(
                noCodeScreenId,
                { screen ->
                    assertEquals(screen.id, noCodeScreenId)
                    assertEquals(screen.background, "#CDFFD7")
                    assertEquals(screen.lang, "EN")
                    assertEquals(screen.obj, "screen")
                    assertTrue(screen.htmlPage.isNotEmpty())

                    signal.countDown()
                },
                { fail("Shouldn't fail") }
            )
        }

        signal.await()
    }

    @Test
    fun views() {
        // given
        val signal = CountDownLatch(1)

        val uid = uidPrefix + "_views"
        val repository = initRepositoryForUid(uid)

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

        val uid = uidPrefix + "_actionPoints"
        val repository = initRepositoryForUid(uid)

        // when
        withNewUserCreated(repository) { error ->
            error?.let {
                fail("Failed to create user")
            }

            repository.actionPoints(
                mapOf(
                    "type" to "screen_view",
                    "active" to "1"
                ),
                { actionPoints ->
                    // no trigger for automation
                    assertTrue(actionPoints === null)

                    signal.countDown()
                },
                { fail("Shouldn't fail") }
            )
        }

        signal.await()
    }

    private fun withNewUserCreated(
        repository: QonversionRepository,
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

    private fun initRepositoryForUid(uid: String): QonversionRepository {
        val qonversionConfig = QonversionConfig.Builder(
            ApplicationProvider.getApplicationContext(),
            "V4pK6FQo3PiDPj_2vYO1qZpNBbFXNP-a",
            QLaunchMode.SubscriptionManagement
        ).build()
        val internalConfig = InternalConfig(qonversionConfig)
        internalConfig.uid = uid
        QDependencyInjector.buildAppComponent(
            qonversionConfig.application,
            internalConfig,
            appStateProvider
        )

        return QDependencyInjector.appComponent.repository()
    }
}
