package io.qonversion.android.sdk.dto.offerings

import io.qonversion.android.sdk.internal.equalsIgnoreOrder

data class QOfferings(
    val main: QOffering?,
    val availableOfferings: List<QOffering> = listOf()
) {
    fun offeringForID(id: String): QOffering? {
        return availableOfferings.firstOrNull { it.offeringID == id }
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is QOfferings &&
                (other === this ||
                        main == other.main &&
                        availableOfferings equalsIgnoreOrder other.availableOfferings)
    }
}
