package com.qonversion.android.sdk.storage

import android.content.SharedPreferences
import com.android.billingclient.api.BillingClient
import com.qonversion.android.sdk.entity.Purchase
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.Type

class DeviceStorage(
    private val preferences: SharedPreferences
) {
    private val moshi = Moshi.Builder().build()
    private val collectionPurchaseType: Type = Types.newParameterizedType(
        MutableSet::class.java,
        Purchase::class.java
    )
    private val jsonAdapter: JsonAdapter<MutableSet<Purchase>> =
        moshi.adapter(collectionPurchaseType)

    fun savePurchase(purchase: Purchase) {
        if (purchase.type == BillingClient.SkuType.INAPP) {

            val purchases = loadPurchases()
            purchases.add(purchase)

            if (purchases.size >= MAX_PURCHASES_NUMBER) {
                val oldPurchases = purchases.toMutableList().take(MAX_OLD_PURCHASES_NUMBER)
                purchases.removeAll(oldPurchases)
            }

            savePurchasesAsJson(purchases)
        }
    }

    fun loadPurchases(): MutableSet<Purchase> {
        val json = preferences.getString(PURCHASE_KEY, "")
        if (json == null || json.isEmpty()) {
            return mutableSetOf()
        }
        return try {
            val purchases: MutableSet<Purchase>? = jsonAdapter.fromJson(json)
            purchases ?: mutableSetOf()
        } catch (e: JsonDataException) {
            mutableSetOf()
        }
    }

    fun clearPurchase(purchase: Purchase) {
        val purchases = loadPurchases()
        purchases.remove(purchase)

        savePurchasesAsJson(purchases)
    }

    private fun savePurchasesAsJson(purchases: MutableSet<Purchase>) {
        val jsonStr: String = jsonAdapter.toJson(purchases)
        preferences.edit().putString(PURCHASE_KEY, jsonStr).apply()
    }

    companion object {
        private const val PURCHASE_KEY = "purchase"
        private const val MAX_PURCHASES_NUMBER = 5
        private const val MAX_OLD_PURCHASES_NUMBER = 2
    }
}