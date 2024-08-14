package com.qonversion.android.sdk.internal.storage

import com.qonversion.android.sdk.dto.QPurchaseOptions
import com.qonversion.android.sdk.internal.purchase.Purchase
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.IOException
import java.lang.reflect.Type

internal class PurchasesCache(
    private val preferences: SharedPreferencesCache
) {
    private val moshi = Moshi.Builder().build()
    private val collectionPurchaseType: Type = Types.newParameterizedType(
        Set::class.java,
        Purchase::class.java
    )
    private val collectionPurchaseOptionsType: Type = Types.newParameterizedType(
        Map::class.java,
        String::class.java,
        QPurchaseOptions::class.java
    )
    private val purchasesJsonAdapter: JsonAdapter<Set<Purchase>> =
        moshi.adapter(collectionPurchaseType)
    private val purchasesOptionsJsonAdapter: JsonAdapter<Map<String, QPurchaseOptions>> =
        moshi.adapter(collectionPurchaseOptionsType)

    fun savePurchase(purchase: Purchase) {
        val purchases = loadPurchases().toMutableSet()
        purchases.add(purchase)

        if (purchases.size >= MAX_PURCHASES_NUMBER) {
            val oldPurchases = purchases.toMutableList().take(MAX_OLD_PURCHASES_NUMBER).toSet()
            purchases.removeAll(oldPurchases)
        }

        savePurchasesAsJson(purchases)
    }

    fun loadPurchases(): Set<Purchase> {
        val json = preferences.getString(PURCHASE_KEY, "")
        if (json.isNullOrEmpty()) {
            return setOf()
        }
        return try {
            val purchases: Set<Purchase>? = purchasesJsonAdapter.fromJson(json)
            purchases ?: setOf()
        } catch (e: IOException) {
            setOf()
        }
    }

    fun clearPurchase(purchase: Purchase) {
        val purchases = loadPurchases().toMutableSet()
        purchases.remove(purchase)

        savePurchasesAsJson(purchases)
    }

    fun saveProcessingPurchasesOptions(options: Map<String, QPurchaseOptions>?) {
        if (options.isNullOrEmpty()) {
            return
        }

        preferences.putObject(PURCHASE_OPTIONS_KEY, options, purchasesOptionsJsonAdapter)
    }

    fun loadProcessingPurchasesOptions(): Map<String, QPurchaseOptions> {
        val purchaseOptions = preferences.getObject(PURCHASE_OPTIONS_KEY, purchasesOptionsJsonAdapter) ?: emptyMap()

        return purchaseOptions
    }

    private fun savePurchasesAsJson(purchases: MutableSet<Purchase>) {
        val jsonStr: String = purchasesJsonAdapter.toJson(purchases)
        preferences.putString(PURCHASE_KEY, jsonStr)
    }

    companion object {
        private const val PURCHASE_OPTIONS_KEY = "purchase_options"
        private const val PURCHASE_KEY = "purchase"
        private const val MAX_PURCHASES_NUMBER = 5
        private const val MAX_OLD_PURCHASES_NUMBER = 1
    }
}
