package com.qonversion.android.sdk.storage

import android.content.SharedPreferences
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

class DeviceStorage(
    private val preferences: SharedPreferences
) {
    fun savePurchase(
        purchase: Purchase,
        skuDetails: SkuDetails?
    ) {
        if (skuDetails?.type == BillingClient.SkuType.INAPP) {

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
        val gson = Gson()
        val json = preferences.getString(PURCHASE, "")
        val type: Type = object : TypeToken<MutableSet<Purchase>>() {}.type
        var result: MutableSet<Purchase>? = gson.fromJson(json, type)

        if (result == null) {
            result = mutableSetOf()
        }
        return result
    }

    fun clearPurchase(purchase: Purchase) {
        val purchases = loadPurchases()
        purchases.remove(purchase)

        savePurchasesAsJson(purchases)
    }

    private fun savePurchasesAsJson(purchases: Set<Purchase>) {
        val gson = Gson()
        val jsonStr = gson.toJson(purchases)
        preferences.edit().putString(PURCHASE, jsonStr).apply()
    }

    companion object {
        private const val PURCHASE = "purchase"
        private const val MAX_PURCHASES_NUMBER = 5
        private const val MAX_OLD_PURCHASES_NUMBER = 2
    }
}