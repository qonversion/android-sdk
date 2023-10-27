package com.qonversion.android.sdk.internal.storage

import com.qonversion.android.sdk.dto.entitlements.QEntitlementSource
import com.qonversion.android.sdk.dto.offerings.QOffering
import com.qonversion.android.sdk.dto.offerings.QOfferingTag
import com.qonversion.android.sdk.dto.offerings.QOfferings
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.dto.products.QProductDuration
import com.qonversion.android.sdk.internal.dto.QProductRenewState
import com.qonversion.android.sdk.dto.products.QProductType
import com.qonversion.android.sdk.internal.dto.QDateAdapter
import com.qonversion.android.sdk.internal.dto.QEligibilityAdapter
import com.qonversion.android.sdk.internal.dto.QEligibilityStatusAdapter
import com.qonversion.android.sdk.internal.dto.QExperimentGroupTypeAdapter
import com.qonversion.android.sdk.internal.dto.QOfferingAdapter
import com.qonversion.android.sdk.internal.dto.QOfferingTagAdapter
import com.qonversion.android.sdk.internal.dto.QOfferingsAdapter
import com.qonversion.android.sdk.internal.dto.QPermission
import com.qonversion.android.sdk.internal.dto.QEntitlementSourceAdapter
import com.qonversion.android.sdk.internal.dto.QLaunchResult
import com.qonversion.android.sdk.internal.dto.QPermissionsAdapter
import com.qonversion.android.sdk.internal.dto.QProductDurationAdapter
import com.qonversion.android.sdk.internal.dto.QProductRenewStateAdapter
import com.qonversion.android.sdk.internal.dto.QProductTypeAdapter
import com.qonversion.android.sdk.internal.dto.QProductsAdapter
import com.squareup.moshi.Moshi
import java.util.*

internal class Util {

    companion object {
        const val CLIENT_ID = "XXXXX"
        const val CLIENT_UID = "XXXX-XXXX-XXXX-XXXX"
        const val CLIENT_TARGET_ID = "XXXXXXXXXXXXXXXXXXXXXXXXX"

        val LAUNCH_RESULT = QLaunchResult(
            uid = "Og-97RMtD0tXhKg-O_ELSQFDpdyuq9Nj",
            date = Date(1612903277000),
            products = mapOf(
                "main" to QProduct(
                    qonversionID = "main",
                    storeID = "qonversion_subs_weekly",
                    basePlanID = null,
                    type = QProductType.Trial,
                    duration = QProductDuration.Weekly
                ),
                "in_app" to QProduct(
                    qonversionID = "in_app",
                    storeID = "qonversion_inapp_consumable",
                    basePlanID = null,
                    type = QProductType.InApp,
                    duration = null
                ),
                "annual" to QProduct(
                    qonversionID = "annual",
                    storeID = "qonversion_subs_annual",
                    basePlanID = null,
                    type = QProductType.Trial,
                    duration = QProductDuration.Annual
                )
            ),
            permissions = mapOf(
                "standart" to QPermission(
                    permissionID = "standart",
                    productID = "in_app",
                    renewState = QProductRenewState.NonRenewable,
                    startedDate = Date(1612880300000),
                    expirationDate = null,
                    source = QEntitlementSource.PlayStore,
                    active = 1
                ),
                "Test Permission" to QPermission(
                    permissionID = "Test Permission",
                    productID = "in_app",
                    renewState = QProductRenewState.NonRenewable,
                    startedDate = Date(1612880300000),
                    expirationDate = null,
                    source = QEntitlementSource.AppStore,
                    active = 1
                )
            ),
            userProducts = mapOf(
                "in_app" to QProduct(
                    qonversionID = "in_app",
                    storeID = "qonversion_inapp_consumable",
                    basePlanID = null,
                    type = QProductType.InApp,
                    duration = null
                )
            ),
            offerings = QOfferings(
                main = QOffering(
                    offeringID = "main",
                    tag = QOfferingTag.Main,
                    products = listOf(
                        QProduct(
                            qonversionID = "in_app",
                            storeID = "qonversion_inapp_consumable",
                            basePlanID = null,
                            type = QProductType.InApp,
                            duration = null
                        ),
                        QProduct(
                            qonversionID = "main",
                            storeID = "qonversion_subs_weekly",
                            basePlanID = null,
                            type = QProductType.Trial,
                            duration = QProductDuration.Weekly
                        )
                    )
                ),
                availableOfferings = listOf(
                    QOffering(
                        offeringID = "main",
                        tag = QOfferingTag.Main,
                        products = listOf(
                            QProduct(
                                qonversionID = "in_app",
                                storeID = "qonversion_inapp_consumable",
                                basePlanID = null,
                                type = QProductType.InApp,
                                duration = null
                            ),
                            QProduct(
                                qonversionID = "main",
                                storeID = "qonversion_subs_weekly",
                                basePlanID = null,
                                type = QProductType.Trial,
                                duration = QProductDuration.Weekly
                            )
                        )
                    )
                )
            )
        )

        const val LAUNCH_RESULT_JSON_STR = "{\"uid\":\"Og-97RMtD0tXhKg-O_ELSQFDpdyuq9Nj\"," +
                "\"timestamp\":1612903277," +
                "\"products\":[{\"id\":\"main\",\"store_id\":\"qonversion_subs_weekly\",\"type\":0,\"duration\":0}," +
                "{\"id\":\"in_app\",\"store_id\":\"qonversion_inapp_consumable\",\"type\":2}," +
                "{\"id\":\"annual\",\"store_id\":\"qonversion_subs_annual\",\"type\":0,\"duration\":4}]," +
                "\"permissions\":[{\"id\":\"standart\",\"associated_product\":\"in_app\",\"renew_state\":-1,\"started_timestamp\":1612880300,\"source\":\"playstore\",\"active\":1},{\"id\":\"Test Permission\",\"associated_product\":\"in_app\",\"renew_state\":-1,\"started_timestamp\":1612880300,\"source\":\"appstore\",\"active\":1}],\"user_products\":[{\"id\":\"in_app\",\"store_id\":\"qonversion_inapp_consumable\",\"type\":2}],\"experiments\":[]," +
                "\"offerings\":[{\"id\":\"main\",\"tag\":1,\"products\":[{\"id\":\"in_app\",\"store_id\":\"qonversion_inapp_consumable\",\"type\":2},{\"id\":\"main\",\"store_id\":\"qonversion_subs_weekly\",\"type\":0,\"duration\":0}],\"experiment\":{\"uid\":\"secondary\",\"attached\":false}" +
                "}]}"

        fun buildMoshi(): Moshi =
            Moshi.Builder()
                .add(QProductDurationAdapter())
                .add(QDateAdapter())
                .add(QProductsAdapter())
                .add(QPermissionsAdapter())
                .add(QProductTypeAdapter())
                .add(QProductRenewStateAdapter())
                .add(QEntitlementSourceAdapter())
                .add(QOfferingsAdapter())
                .add(QOfferingAdapter())
                .add(QOfferingTagAdapter())
                .add(QExperimentGroupTypeAdapter())
                .add(QEligibilityStatusAdapter())
                .add(QEligibilityAdapter())
                .build()
    }
}