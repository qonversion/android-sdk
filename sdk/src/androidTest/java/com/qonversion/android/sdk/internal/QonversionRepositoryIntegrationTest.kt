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
import com.qonversion.android.sdk.dto.eligibility.QIntroEligibilityStatus
import com.qonversion.android.sdk.dto.entitlements.QEntitlementGrantType
import com.qonversion.android.sdk.dto.offerings.QOffering
import com.qonversion.android.sdk.dto.offerings.QOfferingTag
import com.qonversion.android.sdk.dto.offerings.QOfferings
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.dto.properties.QUserProperty
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
private const val INCORRECT_PROJECT_KEY = "V4pK6FQo3PiDPj_2vYO1qZpNBbFXNP-aaaaa"
private val UID_PREFIX = "QON_test_uid_android_" + System.currentTimeMillis()

@RunWith(AndroidJUnit4::class)
internal class QonversionRepositoryIntegrationTest {

    private val appStateProvider = object : AppStateProvider {
        override val appState: AppState
            get() = AppState.Foreground
    }

    private val installDate = 1679652674L

    private val noCodeScreenId = "lsarjYcU"

    private val monthlyProduct = QProduct("test_monthly", "google_monthly", null)
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

                override fun onError(error: QonversionError) {
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
    fun initError() {
        // given
        val signal = CountDownLatch(1)

        val uid = UID_PREFIX + "_init"
        val data = InitRequestData(
            installDate,
            null,
            emptyList(),
            object : QonversionLaunchCallback {
                override fun onSuccess(launchResult: QLaunchResult) {
                    fail("Shouldn't succeed")
                }

                override fun onError(error: QonversionError) {
                    // then
                    assertIncorrectProjectKeyError(error)
                    signal.countDown()
                }
            }
        )

        val repository = initRepository(uid, INCORRECT_PROJECT_KEY)

        // when
        repository.init(data)

        signal.await()
    }

    @Test
    fun purchase() {
        // given
        val signal = CountDownLatch(1)

        val uid = UID_PREFIX + "_purchase"
        val callback = object : QonversionLaunchCallback {
            override fun onSuccess(launchResult: QLaunchResult) {
                // then
                assertEquals(launchResult.uid, uid)
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

            override fun onError(error: QonversionError) {
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
    fun purchaseForExistingUser() {
        // given
        val signal = CountDownLatch(1)

        val expectedPermissions = mapOf(
            "premium" to expectedPremiumPermission()
        )

        val uid = "QON_test_uid1679992132407"
        val callback = object : QonversionLaunchCallback {
            override fun onSuccess(launchResult: QLaunchResult) {
                // then
                assertEquals(launchResult.uid, uid)
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

            override fun onError(error: QonversionError) {
                fail("Shouldn't fail")
            }
        }

        val repository = initRepository(uid)

        // when
        repository.purchase(installDate, purchase, "test_monthly", callback)

        signal.await()
    }

    @Test
    fun purchaseError() {
        // given
        val signal = CountDownLatch(1)

        val uid = UID_PREFIX + "_purchase"
        val callback = object : QonversionLaunchCallback {
            override fun onSuccess(launchResult: QLaunchResult) {
                fail("Shouldn't succeed")
            }

            override fun onError(error: QonversionError) {
                assertIncorrectProjectKeyError(error)
                signal.countDown()
            }
        }

        val repository = initRepository(uid, INCORRECT_PROJECT_KEY)

        // when
        repository.purchase(
            installDate,
            purchase,
            "test_monthly",
            callback
        )

        signal.await()
    }

    @Test
    fun restore() {
        // given
        val signal = CountDownLatch(1)

        val history = listOf(
            History(
                "google_inapp",
                "lcbfeigohklhpdgmpildjabg.AO-J1OyV-EE2bKGqDcRCvqjZ2NI1uHDRuvonRn5RorP6LNsyK7yHK8FaFlXp6bjTEX3-4JvZKtbY_bpquKBfux09Mfkx05M9YGZsfsr5BJk74r719m77Oyo",
                1685953401,
            )
        )

        val expectedPermissions = mapOf(
            "noAds" to QPermission(
                "noAds",
                "test_inapp",
                QProductRenewState.NonRenewable,
                Date(1685953401000),
                null,
                QEntitlementSource.PlayStore,
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

            override fun onError(error: QonversionError) {
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
    fun restoreError() {
        // given
        val signal = CountDownLatch(1)

        val history = listOf(
            History(
                "google_monthly",
                "lgeigljfpmeoddkcebkcepjc.AO-J1Oy305qZj99jXTPEVBN8UZGoYAtjDLj4uTjRQvUFaG0vie-nr6VBlN0qnNDMU8eJR-sI7o3CwQyMOEHKl8eJsoQ86KSFzxKBR07PSpHLI_o7agXhNKY",
                1679933171,
            )
        )

        val uid = UID_PREFIX + "_restore"
        val callback = object : QonversionLaunchCallback {
            override fun onSuccess(launchResult: QLaunchResult) {
                fail("Shouldn't succeed")
            }

            override fun onError(error: QonversionError) {
                assertIncorrectProjectKeyError(error)
                signal.countDown()
            }
        }

        val repository = initRepository(uid, INCORRECT_PROJECT_KEY)

        // when
        repository.restoreRequest(installDate, history, callback)

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
    fun attributionError() {
        // given
        val signal = CountDownLatch(1)
        val testAttributionInfo = mapOf(
            "one" to 3,
            "to be or not" to "be",
            "toma" to "s"
        )

        val uid = UID_PREFIX + "_attribution"
        val repository = initRepository(uid, INCORRECT_PROJECT_KEY)

        // when and then
        repository.attribution(
            testAttributionInfo,
            QAttributionProvider.AppsFlyer.id,
            { fail("Shouldn't succeed") },
            { error ->
                assertAccessDeniedError(error)
                signal.countDown()
            }
        )

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
    fun sendPropertiesError() {
        // given
        val signal = CountDownLatch(1)
        val testProperties = mapOf(
            "customProperty" to "custom property value",
            QUserPropertyKey.CustomUserId.userPropertyCode to "custom user id"
        )

        val uid = UID_PREFIX + "_sendProperties"
        val repository = initRepository(uid, INCORRECT_PROJECT_KEY)

        // when and then
        repository.sendProperties(
            testProperties,
            { fail("Shouldn't succeed") },
            { error ->
                assertAccessDeniedError(error)
                signal.countDown()
            }
        )

        signal.await()
    }

    @Test
    fun getProperties() {
        // given
        val signal = CountDownLatch(1)
        val testProperties = mapOf(
            "customProperty" to "custom property value",
            QUserPropertyKey.CustomUserId.userPropertyCode to "custom user id"
        )
        val expRes = listOf(
            QUserProperty("customProperty", "customProperty"),
            QUserProperty(QUserPropertyKey.CustomUserId.userPropertyCode, "custom user id"),
        )

        val uid = UID_PREFIX + "_getProperties"
        val repository = initRepository(uid)

        // when and then
        withNewUserCreated(repository) { error ->
            error?.let {
                fail("Failed to create user")
            }

            repository.sendProperties(
                testProperties,
                {
                    repository.getProperties(
                        { properties ->
                            properties.equalsIgnoreOrder(expRes)
                            signal.countDown()
                        },
                        {
                            fail("Shouldn't fail")
                        }
                    )
                },
                { fail("Failed to send properties") }
            )
        }

        signal.await()
    }

    @Test
    fun getPropertiesError() {
        // given
        val signal = CountDownLatch(1)

        val uid = UID_PREFIX + "_getProperties"
        val repository = initRepository(uid, INCORRECT_PROJECT_KEY)

        // when and then
        repository.getProperties(
            { fail("Shouldn't succeed") },
            { error ->
                assertAccessDeniedError(error)
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
        val expectedResult = mapOf(
            monthlyProduct.qonversionID to QEligibility(QIntroEligibilityStatus.NonIntroOrTrialProduct),
            annualProduct.qonversionID to QEligibility(QIntroEligibilityStatus.Unknown),
            inappProduct.qonversionID to QEligibility(QIntroEligibilityStatus.NonIntroOrTrialProduct)
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
    fun eligibilityForProductIdsError() {
        // given
        val signal = CountDownLatch(1)
        val productIds = listOf(monthlyProduct.qonversionID, annualProduct.qonversionID)

        val callback = object : QonversionEligibilityCallback {
            override fun onSuccess(eligibilities: Map<String, QEligibility>) {
                fail("Shouldn't succeed")
            }

            override fun onError(error: QonversionError) {
                assertIncorrectProjectKeyError(error)

                signal.countDown()
            }
        }

        val uid = UID_PREFIX + "_eligibilityForProductIds"
        val repository = initRepository(uid, INCORRECT_PROJECT_KEY)

        // when and then
        repository.eligibilityForProductIds(
            productIds,
            installDate,
            callback
        )

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
    fun identifyError() {
        // given
        val signal = CountDownLatch(1)
        val uid = UID_PREFIX + "_identify"
        val identityId = "identity_for_$uid"

        val repository = initRepository(uid, INCORRECT_PROJECT_KEY)

        // when and then
        repository.identify(
            identityId,
            uid,
            { fail("Shouldn't succeed") },
            { error ->
                assertAccessDeniedError(error)

                signal.countDown()
            }
        )

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
    fun screensError() {
        // given
        val signal = CountDownLatch(1)

        val uid = UID_PREFIX + "_screens"
        val repository = initRepository(uid, INCORRECT_PROJECT_KEY)

        // when
        repository.screens(
            noCodeScreenId,
            { fail("Shouldn't succeed") },
            { error ->
                assertAccessDeniedError(error)

                signal.countDown()
            }
        )

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
    fun viewsError() {
        // given
        val signal = CountDownLatch(1)

        val uid = UID_PREFIX + "_views"
        val repository = initRepository(uid, INCORRECT_PROJECT_KEY)

        // when
        repository.views(noCodeScreenId)

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

    @Test
    fun actionPointsError() {
        // given
        val signal = CountDownLatch(1)

        val uid = UID_PREFIX + "_actionPoints"
        val repository = initRepository(uid, INCORRECT_PROJECT_KEY)

        // when
        repository.actionPoints(
            mapOf(
                "type" to "screen_view",
                "active" to "1"
            ),
            { fail("Shouldn't succeed") },
            { error ->
                assertAccessDeniedError(error)

                signal.countDown()
            }
        )

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

                override fun onError(error: QonversionError) {
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
        ).build()
        val internalConfig = InternalConfig(qonversionConfig)
        internalConfig.uid = uid
        QDependencyInjector.buildAppComponent(
            qonversionConfig.application,
            internalConfig,
            appStateProvider
        )

        return QDependencyInjector.appComponent.qonversionRepository()
    }

    private fun assertIncorrectProjectKeyError(error: QonversionError) {
        assertEquals(error.code, QonversionErrorCode.InvalidCredentials)
        assertTrue(listOf(
            """HTTP status code=400, data={"message":"Invalid access token received","code":10003,"status":400,"extra":[]}. """,
            """HTTP status code=401, data={"code":10003,"message":"Invalid access token received"}. """
        ).contains(error.additionalMessage))
    }

    private fun assertAccessDeniedError(error: QonversionError) {
        assertEquals(error.code, QonversionErrorCode.BackendError)
        assertTrue(listOf(
            "HTTP status code=400, . ",
            "HTTP status code=401, error=Authorization error: project not found. ",
            "HTTP status code=401, error=User with specified access token does not exist. "
        ).contains(error.additionalMessage))
    }

    private fun expectedPremiumPermission(): QPermission {
        return QPermission(
            "premium",
            "test_monthly",
            QProductRenewState.Canceled,
            Date(1679933171000),
            Date(1679935273000),
            QEntitlementSource.PlayStore,
            0,
            0,
            null,
            null,
            null,
            null,
            QEntitlementGrantType.Purchase,
            null,
            emptyList()
        )
    }
}
