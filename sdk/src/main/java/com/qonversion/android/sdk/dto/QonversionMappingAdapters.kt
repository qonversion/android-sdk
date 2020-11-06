package com.qonversion.android.sdk.dto

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.util.*

class QProductDurationAdapter {
    @ToJson
    private fun toJson(enum: QProductDuration): Int {
        return enum.type
    }

    @FromJson
    fun fromJson(type: Int): QProductDuration? {
        return QProductDuration.fromType(type)
    }
}

class QProductTypeAdapter {
    @ToJson
    private fun toJson(enum: QProductType): Int {
        return enum.type
    }

    @FromJson
    fun fromJson(type: Int): QProductType {
        return QProductType.fromType(type)
    }
}

class QProductRenewStateAdapter {
    @ToJson
    private fun toJson(enum: QProductRenewState): Int {
        return enum.type
    }

    @FromJson
    fun fromJson(type: Int): QProductRenewState {
        return QProductRenewState.fromType(type)
    }
}

class QDateAdapter {
    @ToJson
    private fun toJson(date: Date): Long {
        return date.time / 1000
    }

    @FromJson
    fun fromJson(date: Long): Date {
        return Date(date * 1000)
    }
}

class QProductsAdapter {
    @ToJson
    private fun toJson(products: Map<String, QProduct>): List<QProduct> {
        return products.values.toList()
    }

    @FromJson
    fun fromJson(products: List<QProduct>): Map<String, QProduct> {
        val result = mutableMapOf<String, QProduct>()
        products.forEach {
            result[it.qonversionID] = it
        }
        return result
    }
}

class QPermissionsAdapter {
    @ToJson
    private fun toJson(permissions: Map<String, QPermission>): List<QPermission> {
        return permissions.values.toList()
    }

    @FromJson
    fun fromJson(permissions: List<QPermission>): Map<String, QPermission> {
        val result = mutableMapOf<String, QPermission>()
        permissions.forEach {
            result[it.permissionID] = it
        }
        return result
    }
}

//class QBoolAdapter {
//    @ToJson
//    private fun toJson(value: Boolean): Int {
//        return if (value) 1 else 0
//    }
//
//    @FromJson
//    fun fromJson(value: Int): Boolean {
//        return value > 0
//    }
//}