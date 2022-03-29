package com.qonversion.android.sdk.internal.billing.dto

import com.android.billingclient.api.BillingClient
import com.qonversion.android.sdk.internal.exception.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class BillingErrorTest {

    @Nested
    inner class ToQonversionExceptionTest {

        @Test
        fun `all cases`() {
            // given
            val billingUnavailableAdditionalInfo = "Billing service is not connected to any Google account at the moment."
            val purchasingAdditionalInfo = "Please make sure that you are using the google account where purchases are allowed " +
                    "and the application was correctly signed and properly set up for billing."
            val conformity = mapOf(
                BillingClient.BillingResponseCode.SERVICE_TIMEOUT to Pair(ErrorCode.PlayStore, ""),
                BillingClient.BillingResponseCode.SERVICE_DISCONNECTED to Pair(ErrorCode.PlayStore, ""),
                BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE to Pair(ErrorCode.PlayStore, ""),
                BillingClient.BillingResponseCode.ERROR to Pair(ErrorCode.PlayStore, ""),
                BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED to Pair(ErrorCode.FeatureNotSupported, ""),
                BillingClient.BillingResponseCode.USER_CANCELED to Pair(ErrorCode.CanceledPurchase, ""),
                BillingClient.BillingResponseCode.BILLING_UNAVAILABLE to Pair(ErrorCode.BillingUnavailable, billingUnavailableAdditionalInfo),
                BillingClient.BillingResponseCode.ITEM_UNAVAILABLE to Pair(ErrorCode.ProductUnavailable, ""),
                BillingClient.BillingResponseCode.DEVELOPER_ERROR to Pair(ErrorCode.Purchasing, purchasingAdditionalInfo),
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED to Pair(ErrorCode.ProductAlreadyOwned, ""),
                BillingClient.BillingResponseCode.ITEM_NOT_OWNED to Pair(ErrorCode.ProductNotOwned, ""),
            )

            conformity.forEach { (billingErrorCode, exceptionData) ->
                val billingError = BillingError(billingErrorCode, "")

                // when
                val res = billingError.toQonversionException()

                // then
                assertThat(res.code).isEqualTo(exceptionData.first)
                assertThat(res.message).isEqualTo(exceptionData.second)
            }
        }
    }
}