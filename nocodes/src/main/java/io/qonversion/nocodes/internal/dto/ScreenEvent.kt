package io.qonversion.nocodes.internal.dto

internal data class ScreenEvent(
    val type: ScreenEventType,
    val screenUid: String,
    val pageIndex: Int? = null,
    val happenedAt: Long = System.currentTimeMillis() / 1000
) {
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "type" to type.value,
            "screen_uid" to screenUid,
            "happened_at" to happenedAt
        )
        pageIndex?.let { map["page_index"] = it }
        return map
    }
}
