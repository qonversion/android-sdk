package com.qonversion.android.sdk.extractor

import org.json.JSONObject

class SkuDetailsTokenExtractor : Extractor<String> {
    override fun extract(json: String?): String {
        if (json.isNullOrEmpty()) {
            return ""
        }

        val jsonObj = JSONObject(json)

        if (jsonObj.has(SKU_DETAILS_TOKEN_KEY)) {
            return jsonObj.getString(SKU_DETAILS_TOKEN_KEY)
        }

        return ""
    }

    companion object {
        const val SKU_DETAILS_TOKEN_KEY = "skuDetailsToken"
    }
}