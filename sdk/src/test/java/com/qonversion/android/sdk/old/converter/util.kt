package com.qonversion.android.sdk.old.converter

class Util {

    companion object {
        const val CORRECT_SKU_DETAILS_SUB_JSON = "{\n" +
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

        const val CORRECT_PURCHASE_SUB_JSON = "{\n" +
                "  \"orderId\": \"GPA.0000-0000-0000-00000\",\n" +
                "  \"packageName\": \"com.qonversion.android.sdk\",\n" +
                "  \"productId\": \"conversion_test_subscribe\",\n" +
                "  \"purchaseTime\": 1575404669520,\n" +
                "  \"purchaseState\": 0,\n" +
                "  \"purchaseToken\": \"XXXXXXX\",\n" +
                "  \"autoRenewing\": true,\n" +
                "  \"acknowledged\": false\n" +
                "}"

        const val CORRECT_SKU_DETAILS_INAPP_JSON = "{\n" +
                "  \"skuDetailsToken\": \"XXXXXXX\",\n" +
                "  \"productId\": \"conversion_test_purchase\",\n" +
                "  \"type\": \"inapp\",\n" +
                "  \"price\": \"RUB 500.00\",\n" +
                "  \"price_amount_micros\": 500000000,\n" +
                "  \"price_currency_code\": \"RUB\",\n" +
                "  \"title\": \"conversion-test-purchase (Qonversion)\",\n" +
                "  \"description\": \"conversion-test-purchase\"\n" +
                "}"

        const val CORRECT_PURCHASE_INAPP_JSON = "{\n" +
                "  \"orderId\": \"GPA.0000-0000-0000-00000\",\n" +
                "  \"packageName\": \"com.qonversion.android.sdk\",\n" +
                "  \"productId\": \"conversion_test_purchase\",\n" +
                "  \"purchaseTime\": 1575404326564,\n" +
                "  \"purchaseState\": 0,\n" +
                "  \"purchaseToken\": \"XXXXXXX\",\n" +
                "  \"acknowledged\": false\n" +
                "}\n"
    }

}