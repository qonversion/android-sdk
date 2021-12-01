package com.qonversion.android.sdk.internal.networkLayer.headerBuilder

internal interface HeaderBuilder {
    fun buildCommonHeaders(): Map<String, String>
}
