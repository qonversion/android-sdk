package com.qonversion.android.sdk.dto

data class QOfferings (
    val main: QOffering?,
    val availableOfferings: List<QOffering> = listOf()
) {
    fun offeringForID(id: String): QOffering? {
        return availableOfferings.firstOrNull { it.offeringID == id }
    }
}