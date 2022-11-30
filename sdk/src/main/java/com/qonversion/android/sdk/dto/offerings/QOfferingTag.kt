package com.qonversion.android.sdk.dto.offerings

enum class QOfferingTag(val tag: Int?) {
    Unknown(-1),
    None(0),
    Main(1);

    companion object {
        fun fromTag(tag: Int?): QOfferingTag {
            return when (tag ?: 0) {
                0 -> None
                1 -> Main
                else -> Unknown
            }
        }
    }
}
