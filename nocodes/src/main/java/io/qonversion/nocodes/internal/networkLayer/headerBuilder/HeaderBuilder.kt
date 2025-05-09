package io.qonversion.nocodes.internal.networkLayer.headerBuilder

internal interface HeaderBuilder {

    fun buildCommonHeaders(): Map<String, String>
}
