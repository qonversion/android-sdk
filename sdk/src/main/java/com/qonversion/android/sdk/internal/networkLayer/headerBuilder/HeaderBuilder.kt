package com.qonversion.android.sdk.internal.networkLayer.headerBuilder

interface HeaderBuilder {
    fun buildCommonHeaders(): Map<String, String>
}
