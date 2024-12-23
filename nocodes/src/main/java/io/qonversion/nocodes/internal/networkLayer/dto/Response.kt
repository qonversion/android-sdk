package io.qonversion.nocodes.internal.networkLayer.dto

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

        val mapData: Map<*, *> get() = getTypedData()

        @Throws(QonversionException::class)
        inline fun <reified T> getTypedData(): T {
            return if (data is T) {
                data
            } else {
                throw QonversionException(ErrorCode.Mapping)
            }
        }
    }
}