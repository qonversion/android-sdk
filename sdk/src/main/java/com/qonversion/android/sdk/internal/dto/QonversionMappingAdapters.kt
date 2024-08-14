package com.qonversion.android.sdk.internal.dto

import com.qonversion.android.sdk.dto.QRemoteConfig
import com.qonversion.android.sdk.dto.QRemoteConfigList
import com.qonversion.android.sdk.dto.QRemoteConfigurationAssignmentType
import com.qonversion.android.sdk.dto.QRemoteConfigurationSourceType
import com.qonversion.android.sdk.dto.entitlements.QEntitlementSource
import com.qonversion.android.sdk.internal.milliSecondsToSeconds
import com.qonversion.android.sdk.internal.secondsToMilliSeconds
import com.qonversion.android.sdk.internal.dto.eligibility.ProductEligibility
import com.qonversion.android.sdk.dto.eligibility.QEligibility
import com.qonversion.android.sdk.dto.eligibility.QIntroEligibilityStatus
import com.qonversion.android.sdk.dto.entitlements.QEntitlementGrantType
import com.qonversion.android.sdk.dto.entitlements.QTransactionEnvironment
import com.qonversion.android.sdk.dto.entitlements.QTransactionOwnershipType
import com.qonversion.android.sdk.dto.entitlements.QTransactionType
import com.qonversion.android.sdk.dto.experiments.QExperimentGroupType
import com.qonversion.android.sdk.dto.offerings.QOffering
import com.qonversion.android.sdk.dto.offerings.QOfferingTag
import com.qonversion.android.sdk.dto.offerings.QOfferings
import com.qonversion.android.sdk.dto.products.QProduct
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.util.Date

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

internal class QEntitlementSourceAdapter {
    @ToJson
    private fun toJson(enum: QEntitlementSource): String {
        return enum.key
    }

    @FromJson
    fun fromJson(key: String): QEntitlementSource {
        return QEntitlementSource.fromKey(key)
    }
}

internal class QEntitlementGrantTypeAdapter {
    @ToJson
    private fun toJson(enum: QEntitlementGrantType): String {
        return enum.type
    }

    @FromJson
    fun fromJson(type: String): QEntitlementGrantType {
        return QEntitlementGrantType.fromType(type)
    }
}

internal class QTransactionEnvironmentAdapter {
    @ToJson
    private fun toJson(enum: QTransactionEnvironment): String {
        return enum.type
    }

    @FromJson
    fun fromJson(type: String): QTransactionEnvironment {
        return QTransactionEnvironment.fromType(type)
    }
}

internal class QTransactionOwnershipTypeAdapter {
    @ToJson
    private fun toJson(enum: QTransactionOwnershipType): String {
        return enum.type
    }

    @FromJson
    fun fromJson(type: String): QTransactionOwnershipType {
        return QTransactionOwnershipType.fromType(type)
    }
}

internal class QTransactionTypeAdapter {
    @ToJson
    private fun toJson(enum: QTransactionType): String {
        return enum.type
    }

    @FromJson
    fun fromJson(type: String): QTransactionType {
        return QTransactionType.fromType(type)
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

internal class QPurchaseOptions {
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

internal class QExperimentGroupTypeAdapter {
    @ToJson
    private fun toJson(enum: QExperimentGroupType): String {
        return enum.type
    }

    @FromJson
    fun fromJson(type: String): QExperimentGroupType {
        return QExperimentGroupType.fromType(type)
    }
}

internal class QRemoteConfigurationSourceAssignmentTypeAdapter {
    @ToJson
    private fun toJson(enum: QRemoteConfigurationAssignmentType): String {
        return enum.type
    }

    @FromJson
    fun fromJson(type: String): QRemoteConfigurationAssignmentType {
        return QRemoteConfigurationAssignmentType.fromType(type)
    }
}

internal class QRemoteConfigurationSourceTypeAdapter {
    @ToJson
    private fun toJson(enum: QRemoteConfigurationSourceType): String {
        return enum.type
    }

    @FromJson
    fun fromJson(type: String): QRemoteConfigurationSourceType {
        return QRemoteConfigurationSourceType.fromType(type)
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

internal class QRemoteConfigListAdapter {
    @ToJson
    private fun toJson(remoteConfigList: QRemoteConfigList?): List<QRemoteConfig> {
        return remoteConfigList?.remoteConfigs ?: listOf()
    }

    @FromJson
    fun fromJson(remoteConfigs: List<QRemoteConfig>): QRemoteConfigList? {
        if (remoteConfigs.isEmpty()) {
            return null
        }

        return QRemoteConfigList(remoteConfigs)
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
