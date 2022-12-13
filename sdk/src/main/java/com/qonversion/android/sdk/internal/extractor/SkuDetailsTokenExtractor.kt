package com.qonversion.android.sdk.internal.extractor

import org.json.JSONObject

internal class SkuDetailsTokenExtractor : Extractor<String> {
    override fun extract(response: String?): String {
        if (response.isNullOrEmpty()) {
            return ""
        }

        val jsonObj = JSONObject(response)

        if (jsonObj.has(SKU_DETAILS_TOKEN_KEY)) {
            return jsonObj.getString(SKU_DETAILS_TOKEN_KEY)
        }

        return ""
    }

    companion object {
        const val SKU_DETAILS_TOKEN_KEY = "skuDetailsToken"
    }
}
