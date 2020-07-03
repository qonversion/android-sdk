package com.qonversion.android.sdk.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qonversion.android.sdk.purchasequeue.Util.Companion.SUBSCRIPTION
import com.qonversion.android.sdk.purchasequeue.Util.Companion.purchaseWithName
import com.qonversion.android.sdk.storage.db.QonversionDatabase
import com.qonversion.android.sdk.storage.db.dao.PurchaseInfoDao
import com.qonversion.android.sdk.storage.purchase.PurchaseDataSource
import com.qonversion.android.sdk.storage.purchase.PurchaseLocalDataSource
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class PurchaseDataSourceTest {

    private lateinit var purchaseDataSource: PurchaseDataSource
    private lateinit var purchaseInfoDao: PurchaseInfoDao
    private lateinit var db: QonversionDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room
            .inMemoryDatabaseBuilder(context, QonversionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        purchaseInfoDao = db.purchaseInfo()
        purchaseDataSource = PurchaseLocalDataSource(
            purchaseInfoDao
        )
    }

    @Test
    @Throws(Exception::class)
    fun simpleSingleInsertionTest() {
        val rawId = purchaseDataSource.savePurchase(SUBSCRIPTION)
        Assert.assertTrue(rawId > 0)
        Assert.assertTrue(purchaseDataSource.count() == 1)
        Assert.assertTrue(purchaseDataSource.isPurchaseExist(SUBSCRIPTION))
    }

    @Test
    @Throws(Exception::class)
    fun duplicateInsertionReplacementTest() {
        val rawId = purchaseDataSource.savePurchase(SUBSCRIPTION)
        val newRawId = purchaseDataSource.savePurchase(SUBSCRIPTION)
        Assert.assertTrue(rawId > 0)
        Assert.assertTrue(newRawId > 0)
        Assert.assertTrue(newRawId == rawId)
        Assert.assertTrue(purchaseDataSource.count() == 1)
        Assert.assertTrue(purchaseDataSource.isPurchaseExist(SUBSCRIPTION))
    }

    @Test
    @Throws(Exception::class)
    fun manyUniquePurchasesInsertionTest() {
        purchaseDataSource.savePurchase(purchaseWithName("unique_purchase_1"))
        purchaseDataSource.savePurchase(purchaseWithName("unique_purchase_2"))
        purchaseDataSource.savePurchase(purchaseWithName("unique_purchase_3"))
        purchaseDataSource.savePurchase(purchaseWithName("unique_purchase_4"))
        purchaseDataSource.savePurchase(purchaseWithName("unique_purchase_5"))
        purchaseDataSource.savePurchase(purchaseWithName("unique_purchase_6"))
        purchaseDataSource.savePurchase(purchaseWithName("unique_purchase_7"))
        purchaseDataSource.savePurchase(purchaseWithName("unique_purchase_8"))
        purchaseDataSource.savePurchase(purchaseWithName("unique_purchase_9"))
        Assert.assertTrue(purchaseDataSource.count() == 9)
        Assert.assertTrue(purchaseDataSource.isPurchaseExist(purchaseWithName("unique_purchase_1")))
        Assert.assertTrue(purchaseDataSource.isPurchaseExist(purchaseWithName("unique_purchase_2")))
        Assert.assertTrue(purchaseDataSource.isPurchaseExist(purchaseWithName("unique_purchase_3")))
        Assert.assertTrue(purchaseDataSource.isPurchaseExist(purchaseWithName("unique_purchase_4")))
        Assert.assertTrue(purchaseDataSource.isPurchaseExist(purchaseWithName("unique_purchase_5")))
        Assert.assertTrue(purchaseDataSource.isPurchaseExist(purchaseWithName("unique_purchase_6")))
        Assert.assertTrue(purchaseDataSource.isPurchaseExist(purchaseWithName("unique_purchase_7")))
        Assert.assertTrue(purchaseDataSource.isPurchaseExist(purchaseWithName("unique_purchase_8")))
        Assert.assertTrue(purchaseDataSource.isPurchaseExist(purchaseWithName("unique_purchase_9")))
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }
}