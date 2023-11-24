package com.qonversion.android.sdk.internal.storage

import android.content.SharedPreferences
import com.qonversion.android.sdk.internal.purchase.Purchase
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.IOException
import java.lang.reflect.Type

internal class PurchasesCache(
    private val preferences: SharedPreferences
) {
    private val moshi = Moshi.Builder().build()
    private val collectionPurchaseType: Type = Types.newParameterizedType(
        Set::class.java,
        Purchase::class.java
    )
    private val jsonAdapter: JsonAdapter<Set<Purchase>> =
        moshi.adapter(collectionPurchaseType)

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
            val purchases: Set<Purchase>? = jsonAdapter.fromJson(json)
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

    private fun savePurchasesAsJson(purchases: MutableSet<Purchase>) {
        val jsonStr: String = jsonAdapter.toJson(purchases)
        preferences.edit().putString(PURCHASE_KEY, jsonStr).apply()
    }

    companion object {
        private const val PURCHASE_KEY = "purchase"
        private const val MAX_PURCHASES_NUMBER = 5
        private const val MAX_OLD_PURCHASES_NUMBER = 1
    }
}
