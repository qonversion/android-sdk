package com.qonversion.android.sdk.purchasequeue

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.qonversion.android.sdk.ErrorHandler
import com.qonversion.android.sdk.PurchaseSendingQueue
import com.qonversion.android.sdk.RequestFactory
import com.qonversion.android.sdk.api.Api
import com.qonversion.android.sdk.api.RxErrorHandlingCallAdapterFactory
import com.qonversion.android.sdk.logger.ConsoleLogger
import com.qonversion.android.sdk.storage.TokenStorage
import com.qonversion.android.sdk.storage.db.QonversionDatabase
import com.qonversion.android.sdk.storage.purchase.PurchaseDataSource
import com.qonversion.android.sdk.storage.purchase.PurchaseLocalDataSource
import com.qonversion.android.sdk.validator.TokenValidator
import com.qonversion.android.sdk.validator.Validator
import com.squareup.moshi.Moshi
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subscribers.TestSubscriber
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException


@RunWith(RobolectricTestRunner::class)
class PurchaseQueueForbiddenServerTest {

    private lateinit var api: Api

    private lateinit var db: QonversionDatabase

    private lateinit var purchaseSendingQueue: PurchaseSendingQueue

    private lateinit var purchaseDataSource: PurchaseDataSource

    private val disposable: CompositeDisposable = CompositeDisposable()

    @Before
    fun setup() {
        val moshi = Moshi.Builder().build()

        val client = Util.getMockHttpClient(ForbiddenServerSimulation())

        val retrofit = Retrofit.Builder()
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .addCallAdapterFactory(RxErrorHandlingCallAdapterFactory.create())
            .baseUrl("https://mock.com")
            .client(client)
            .build()

        api = retrofit.create(Api::class.java)

        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room
            .inMemoryDatabaseBuilder(context, QonversionDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        purchaseDataSource = PurchaseLocalDataSource(
            db.purchaseInfo()
        )

        purchaseSendingQueue = PurchaseSendingQueue(
            purchaseDataSource = purchaseDataSource,
            api = api,
            logger = ConsoleLogger(),
            requestFactory = RequestFactory(
                environmentProvider = MockEnvironmentProvider(),
                internalUserId = "",
                key = "",
                sdkVersion = "",
                trackingEnabled = true

            ),
            storage = TokenStorage(
                SPMockBuilder().createSharedPreferences(),
                TokenValidator() as Validator<String>
            ),
            scheduler = Schedulers.trampoline(),
            errorHandler = ErrorHandler(ConsoleLogger())
        )
    }

    @Test
    @Throws(Exception::class)
    fun sendOnlyUniquePurchases() {
        val uniquePurchaseCount = 3
        val uniqueSubscriptionCount = 4
        val subscriber = TestSubscriber<Long>()
        purchaseSendingQueue.purchasesQueue().subscribe(subscriber)
        disposable.add(subscriber)
        var i = 0
        while (i != uniquePurchaseCount) {
            purchaseSendingQueue.addPurchase(Util.purchaseWithName("unique $i"))
            i++
        }
        var j = 0
        while (j != uniqueSubscriptionCount) {
            purchaseSendingQueue.addPurchase(Util.subscriptionWithName("unique $j"))
            j++
        }
        subscriber.assertNoErrors()
        Assert.assertEquals(0, purchaseDataSource.count())
    }

    @Test
    @Throws(Exception::class)
    fun sendSingleSamePurchaseManyTime() {
        val subscriber = TestSubscriber<Long>()
        purchaseSendingQueue.purchasesQueue().subscribe(subscriber)
        disposable.add(subscriber)
        var i = 0
        while (i != 10) {
            purchaseSendingQueue.addPurchase(Util.purchaseWithName("purchase"))
            i++
        }
        subscriber.assertNoErrors()
        Assert.assertEquals(0, purchaseDataSource.count())
    }

    @Test
    @Throws(Exception::class)
    fun sendManySamePurchasesManyTime() {
        val subscriber = TestSubscriber<Long>()
        purchaseSendingQueue.purchasesQueue().subscribe(subscriber)
        disposable.add(subscriber)
        var i = 0
        while (i != 10) {
            purchaseSendingQueue.addPurchase(Util.purchaseWithName("purchase_1"))
            purchaseSendingQueue.addPurchase(Util.purchaseWithName("purchase_2"))
            i++
        }
        subscriber.assertNoErrors()
        Assert.assertEquals(0, purchaseDataSource.count())
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        disposable.dispose()
        db.close()
    }
}