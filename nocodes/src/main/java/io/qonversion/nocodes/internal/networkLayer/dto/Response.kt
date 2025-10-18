package io.qonversion.nocodes.internal.networkLayer.dto

import io.qonversion.nocodes.error.NoCodesException

internal sealed class Response(
    val code: Int
) {
    class Error(
        code: Int,
        val message: String,
        val type: String? = null,
        val apiCode: String? = null
    ) : Response(code)

    class Success(code: Int, val data: Any) : Response(code) {

        val mapData: Map<*, *>? get() = getTypedData()

        val arrayData: List<*>? get() = getTypedData()

        @Throws(NoCodesException::class)
        inline fun <reified T> getTypedData() = data as? T
    }
}
