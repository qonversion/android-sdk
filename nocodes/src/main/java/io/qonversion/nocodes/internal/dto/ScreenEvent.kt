package io.qonversion.nocodes.internal.dto

internal data class ScreenEvent(
    val data: Map<String, Any>
) {
    fun toMap(): Map<String, Any> = data
}
