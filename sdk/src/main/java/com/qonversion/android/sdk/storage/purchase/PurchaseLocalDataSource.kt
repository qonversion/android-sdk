package com.qonversion.android.sdk.storage.purchase

import com.qonversion.android.sdk.entity.Purchase
import com.qonversion.android.sdk.storage.db.dao.PurchaseInfoDao
import com.qonversion.android.sdk.storage.db.entity.PurchaseInfoEntity

class PurchaseLocalDataSource(
    private val purchaseInfoDao: PurchaseInfoDao
) : PurchaseDataSource {

    override fun savePurchase(purchase: Purchase): Long {
        return purchaseInfoDao.insertOrUpdate(purchase = PurchaseInfoEntity(info = purchase))
    }

    override fun isPurchaseExist(purchase: Purchase): Boolean {
        return purchaseInfoDao.exist(purchase)
    }

    override fun count(): Int {
        return purchaseInfoDao.count()
    }
}