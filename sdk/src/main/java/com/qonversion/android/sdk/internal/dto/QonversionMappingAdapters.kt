package com.qonversion.android.sdk.internal.dto

import com.qonversion.android.sdk.dto.QPermission
import com.qonversion.android.sdk.dto.QPermissionSource
import com.qonversion.android.sdk.internal.billing.milliSecondsToSeconds
import com.qonversion.android.sdk.internal.billing.secondsToMilliSeconds
import com.qonversion.android.sdk.internal.dto.eligibility.ProductEligibility
import com.qonversion.android.sdk.dto.eligibility.QEligibility
import com.qonversion.android.sdk.dto.eligibility.QIntroEligibilityStatus
import com.qonversion.android.sdk.dto.experiments.QExperimentGroupType
import com.qonversion.android.sdk.dto.experiments.QExperimentInfo
import com.qonversion.android.sdk.dto.offerings.QOffering
import com.qonversion.android.sdk.dto.offerings.QOfferingTag
import com.qonversion.android.sdk.dto.offerings.QOfferings
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.dto.products.QProductDuration
import com.qonversion.android.sdk.dto.products.QProductRenewState
import com.qonversion.android.sdk.dto.products.QProductType
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.util.Date

internal class QProductDurationAdapter {
    @ToJson
    private fun toJson(enum: QProductDuration): Int {
        return enum.type
    }

    @FromJson
    fun fromJson(type: Int): QProductDuration? {
        return QProductDuration.fromType(type)
    }
}

internal class QProductTypeAdapter {
    @ToJson
    private fun toJson(enum: QProductType): Int {
        return enum.type
    }

    @FromJson
    fun fromJson(type: Int): QProductType {
        return QProductType.fromType(type)
    }
}

internal class QProductRenewStateAdapter {
    @ToJson
    private fun toJson(enum: QProductRenewState): Int {
        return enum.type
    }

    @FromJson
    fun fromJson(type: Int): QProductRenewState {
        return QProductRenewState.fromType(type)
    }
}

internal class QPermissionSourceAdapter {
    @ToJson
    private fun toJson(enum: QPermissionSource): String {
        return enum.key
    }

    @FromJson
    fun fromJson(key: String): QPermissionSource {
        return QPermissionSource.fromKey(key)
    }
}

internal class QDateAdapter {
    @ToJson
    private fun toJson(date: Date): Long {
        return date.time.milliSecondsToSeconds()
    }

    @FromJson
    fun fromJson(date: Long): Date {
        return Date(date.secondsToMilliSeconds())
    }
}

internal class QProductsAdapter {
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

internal class QPermissionsAdapter {
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

internal class QExperimentsAdapter {
    @ToJson
    private fun toJson(experiments: Map<String, QExperimentInfo>): List<QExperimentInfo> {
        return experiments.values.toList()
    }

    @FromJson
    fun fromJson(experiments: List<QExperimentInfo>): Map<String, QExperimentInfo> {
        val result = mutableMapOf<String, QExperimentInfo>()
        experiments.forEach {
            result[it.experimentID] = it
        }
        return result
    }
}

internal class QExperimentGroupTypeAdapter {
    @ToJson
    private fun toJson(enum: QExperimentGroupType): Int {
        return enum.type
    }

    @FromJson
    fun fromJson(type: Int): QExperimentGroupType {
        return QExperimentGroupType.fromType(type)
    }
}

internal class QOfferingTagAdapter {
    @ToJson
    private fun toJson(enum: QOfferingTag): Int? {
        return enum.tag
    }

    @FromJson
    fun fromJson(tag: Int?): QOfferingTag {
        return QOfferingTag.fromTag(tag)
    }
}

internal class QOfferingsAdapter {
    @ToJson
    private fun toJson(offerings: QOfferings?): List<QOffering> {
        return offerings?.availableOfferings ?: listOf()
    }

    @FromJson
    fun fromJson(offerings: List<QOffering>): QOfferings? {
        if (offerings.isEmpty()) {
            return null
        }

        val main = offerings.firstOrNull { it.tag == QOfferingTag.Main }

        return QOfferings(
            main,
            offerings
        )
    }
}

internal class QOfferingAdapter {
    @FromJson
    fun fromJson(offering: QOffering): QOffering {
        offering.products.forEach {
            it.offeringID = offering.offeringID
        }
        return offering
    }
}

internal class QEligibilityStatusAdapter {
    @ToJson
    private fun toJson(enum: QIntroEligibilityStatus): String {
        return enum.type
    }

    @FromJson
    fun fromJson(type: String): QIntroEligibilityStatus {
        return QIntroEligibilityStatus.fromType(type)
    }
}

internal class QEligibilityAdapter {
    @ToJson
    private fun toJson(
        @Suppress("UNUSED_PARAMETER") eligibilities: Map<String, QEligibility>
    ): List<ProductEligibility> {
        return listOf()
    }

    @FromJson
    fun fromJson(eligibilities: List<ProductEligibility>): Map<String, QEligibility> {
        val result = mutableMapOf<String, QEligibility>()
        eligibilities.forEach {
            result[it.product.qonversionID] = QEligibility(it.eligibilityStatus)
        }
        return result
    }
}
