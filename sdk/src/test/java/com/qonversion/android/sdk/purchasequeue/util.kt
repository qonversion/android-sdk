package com.qonversion.android.sdk.purchasequeue

import android.util.Pair
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.converter.GooglePurchaseConverter
import com.qonversion.android.sdk.extractor.SkuDetailsTokenExtractor
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class Util {

    companion object {
        const val SKU_DETAILS_SUB_JSON = "{\n" +
                "  \"skuDetailsToken\": \"XXXXXXX\",\n" +
                "  \"productId\": \"conversion_test_subscribe\",\n" +
                "  \"type\": \"subs\",\n" +
                "  \"price\": \"RUB 200.00\",\n" +
                "  \"price_amount_micros\": 200000000,\n" +
                "  \"price_currency_code\": \"RUB\",\n" +
                "  \"subscriptionPeriod\": \"P1M\",\n" +
                "  \"freeTrialPeriod\": \"P3D\",\n" +
                "  \"introductoryPriceAmountMicros\": 100000000,\n" +
                "  \"introductoryPricePeriod\": \"P1M\",\n" +
                "  \"introductoryPrice\": \"RUB 100.00\",\n" +
                "  \"introductoryPriceCycles\": 1,\n" +
                "  \"title\": \"conversion-test-subscribe (Qonversion)\",\n" +
                "  \"description\": \"conversion-test-subscribe\"\n" +
                "}\n"

        const val PURCHASE_SUB_JSON = "{\n" +
                "  \"orderId\": \"GPA.0000-0000-0000-00000\",\n" +
                "  \"packageName\": \"com.qonversion.android.sdk\",\n" +
                "  \"productId\": \"conversion_test_subscribe\",\n" +
                "  \"purchaseTime\": 1575404669520,\n" +
                "  \"purchaseState\": 0,\n" +
                "  \"purchaseToken\": \"XXXXXXX\",\n" +
                "  \"autoRenewing\": true,\n" +
                "  \"acknowledged\": false\n" +
                "}"



        const val SKU_DETAILS_INAPP_JSON = "{\n" +
                "  \"skuDetailsToken\": \"XXXXXXX\",\n" +
                "  \"productId\": \"conversion_test_purchase\",\n" +
                "  \"type\": \"inapp\",\n" +
                "  \"price\": \"RUB 500.00\",\n" +
                "  \"price_amount_micros\": 500000000,\n" +
                "  \"price_currency_code\": \"RUB\",\n" +
                "  \"title\": \"conversion-test-purchase (Qonversion)\",\n" +
                "  \"description\": \"conversion-test-purchase\"\n" +
                "}"

        const val PURCHASE_INAPP_JSON = "{\n" +
                "  \"orderId\": \"GPA.0000-0000-0000-00000\",\n" +
                "  \"packageName\": \"com.qonversion.android.sdk\",\n" +
                "  \"productId\": \"conversion_test_purchase\",\n" +
                "  \"purchaseTime\": 1575404326564,\n" +
                "  \"purchaseState\": 0,\n" +
                "  \"purchaseToken\": \"XXXXXXX\",\n" +
                "  \"acknowledged\": false\n" +
                "}\n"

        val PURCHASE = GooglePurchaseConverter(SkuDetailsTokenExtractor())
            .convert(
                Pair(
                    SkuDetails(SKU_DETAILS_INAPP_JSON),
                    Purchase(PURCHASE_INAPP_JSON, "INAPP")
                )
            )

        val SUBSCRIPTION = GooglePurchaseConverter(SkuDetailsTokenExtractor())
            .convert(
                Pair(
                    SkuDetails(SKU_DETAILS_SUB_JSON),
                    Purchase(PURCHASE_SUB_JSON, "SKU")
                )
            )

        fun purchaseWithName(name: String) : com.qonversion.android.sdk.entity.Purchase {
            return GooglePurchaseConverter(SkuDetailsTokenExtractor())
                .convert(
                    Pair(
                        SkuDetails("{\n" +
                                "  \"skuDetailsToken\": \"XXXXXXX\",\n" +
                                "  \"productId\": \"conversion_test_purchase\",\n" +
                                "  \"type\": \"inapp\",\n" +
                                "  \"price\": \"RUB 500.00\",\n" +
                                "  \"price_amount_micros\": 500000000,\n" +
                                "  \"price_currency_code\": \"RUB\",\n" +
                                "  \"title\": \"$name\",\n" +
                                "  \"description\": \"conversion-test-purchase\"\n" +
                                "}"),
                        Purchase(PURCHASE_INAPP_JSON, "INAPP")
                    )
                )
        }

        fun subscriptionWithName(name: String) : com.qonversion.android.sdk.entity.Purchase {
            return GooglePurchaseConverter(SkuDetailsTokenExtractor())
                .convert(
                    Pair(
                        SkuDetails("{\n" +
                                "  \"skuDetailsToken\": \"XXXXXXX\",\n" +
                                "  \"productId\": \"conversion_test_subscribe\",\n" +
                                "  \"type\": \"subs\",\n" +
                                "  \"price\": \"RUB 200.00\",\n" +
                                "  \"price_amount_micros\": 200000000,\n" +
                                "  \"price_currency_code\": \"RUB\",\n" +
                                "  \"subscriptionPeriod\": \"P1M\",\n" +
                                "  \"freeTrialPeriod\": \"P3D\",\n" +
                                "  \"introductoryPriceAmountMicros\": 100000000,\n" +
                                "  \"introductoryPricePeriod\": \"P1M\",\n" +
                                "  \"introductoryPrice\": \"RUB 100.00\",\n" +
                                "  \"introductoryPriceCycles\": 1,\n" +
                                "  \"title\": \"$name\",\n" +
                                "  \"description\": \"conversion-test-subscribe\"\n" +
                                "}\n"),
                        Purchase(PURCHASE_SUB_JSON, "SKU")
                    )
                )
        }

        fun getMockHttpClient(interceptor: Interceptor): OkHttpClient {
            // Create a trust manager that does not validate certificate chains
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                }

                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                }

                override fun getAcceptedIssuers() = arrayOf<X509Certificate>()
            })

            // Install the all-trusting trust manager
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            // Create an ssl socket factory with our all-trusting manager
            val sslSocketFactory = sslContext.socketFactory

            return OkHttpClient.Builder()
                .addInterceptor(interceptor)
                .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }.build()
        }

    }


}