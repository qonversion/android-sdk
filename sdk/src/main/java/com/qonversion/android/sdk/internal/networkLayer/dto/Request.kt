package com.qonversion.android.sdk.internal.networkLayer.dto

class Request(
    val url: String,
    val type: Type,
    val headers: Map<String, String>,
    val body: Map<String, Any?>? = null
) {
    companion object {
        fun post(
            url: String,
            headers: Map<String, String>,
            body: Map<String, Any?>
        ) = Request(url, Type.POST, headers, body)

        fun get(url: String, headers: Map<String, String>) = Request(url, Type.POST, headers)
    }

    enum class Type {
        POST,
        GET,
        DELETE,
        UPDATE
    }
}
