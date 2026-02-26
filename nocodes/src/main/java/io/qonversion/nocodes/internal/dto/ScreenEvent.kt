package io.qonversion.nocodes.internal.dto

internal data class ScreenEvent(
    val type: ScreenEventType,
    val screenUid: String,
    val pageIndex: Int? = null,
    val happenedAt: Long = System.currentTimeMillis() / MILLIS_PER_SECOND
) {
    companion object {
        private const val MILLIS_PER_SECOND = 1000L
    }

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
