package io.qonversion.nocodes.internal.dto

internal data class ScreenEvent(
    val data: Map<String, Any>,
    val screenUid: String,
    val happenedAt: Long = System.currentTimeMillis() / MILLIS_PER_SECOND
) {
    companion object {
        private const val MILLIS_PER_SECOND = 1000L
    }

    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        map.putAll(data)  // All JS-provided fields first
        map["screen_uid"] = screenUid  // SDK enrichment (override if JS sent it)
        map["happened_at"] = happenedAt
        return map
    }
}
