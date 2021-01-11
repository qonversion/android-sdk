package com.qonversion.android.sdk.dto

enum class QOfferingTag(val tag: Int?) {
    None(0),
    Main(1);

    companion object {
        fun fromTag(tag: Int?): QOfferingTag {
            return when (tag ?: 0) {
                1 -> Main
                else -> None
            }
        }
    }
}