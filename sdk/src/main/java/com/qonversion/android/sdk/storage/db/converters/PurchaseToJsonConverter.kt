package com.qonversion.android.sdk.storage.db.converters

import android.util.Log
import androidx.room.TypeConverter
import com.qonversion.android.sdk.entity.Purchase
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi

class PurchaseToJsonConverter {

    private val jsonAdapter: JsonAdapter<Purchase>

    init {
        val moshi = Moshi.Builder().build()
        jsonAdapter = moshi.adapter<Purchase>(Purchase::class.java)
    }

    @TypeConverter
    fun toJson(purchase: Purchase): String {
        val json = jsonAdapter.toJson(purchase)
        Log.println(Log.DEBUG, "Qonversion", "${Thread.currentThread().name} - toJson - length: ${json.length}")
        return json
    }

    @TypeConverter
    fun fromJson(json: String): Purchase? {
        val purchase = jsonAdapter.fromJson(json)
        Log.println(Log.DEBUG, "Qonversion", "${Thread.currentThread().name} - fromJson: ${purchase?.title}")
        return purchase
    }
}