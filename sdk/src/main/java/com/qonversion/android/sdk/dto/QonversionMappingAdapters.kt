package com.qonversion.android.sdk.dto

import com.qonversion.android.sdk.billing.milliSecondsToSeconds
import com.qonversion.android.sdk.billing.secondsToMilliSeconds
import com.qonversion.android.sdk.dto.eligibility.QIntroEligibilityStatus
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
        return date.time.milliSecondsToSeconds()
    }

    @FromJson
    fun fromJson(date: Long): Date {
        return Date(date.secondsToMilliSeconds())
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

class QOfferingTagAdapter {
    @ToJson
    private fun toJson(enum: QOfferingTag): Int? {
        return enum.tag
    }

    @FromJson
    fun fromJson(tag: Int?): QOfferingTag {
        return QOfferingTag.fromTag(tag)
    }
}

class QOfferingsAdapter {
    @ToJson
    private fun toJson(offerings: QOfferings?): String? {
        return null
    }

    @FromJson
    fun fromJson(offerings: List<QOffering>): QOfferings? {
        if (offerings.isEmpty()) {
            return null
        }

        val main = offerings.firstOrNull { it.tag == QOfferingTag.Main }

        return QOfferings(main, offerings)
    }
}

class QEligibilityStatusAdapter {
    @ToJson
    private fun toJson(enum: QIntroEligibilityStatus): String {
        return enum.type
    }

    @FromJson
    fun fromJson(type: String): QIntroEligibilityStatus? {
        return QIntroEligibilityStatus.fromType(type)
    }
}