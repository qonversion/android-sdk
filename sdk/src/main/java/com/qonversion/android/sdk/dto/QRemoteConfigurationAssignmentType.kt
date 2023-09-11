package com.qonversion.android.sdk.dto

enum class QRemoteConfigurationAssignmentType(val type: String) {
    Auto("auto"),
    Manual("manual"),
    Unknown("unknown");

    companion object {
        fun fromType(type: String): QRemoteConfigurationAssignmentType {
            return when (type) {
                "auto" -> Auto
                "manual" -> Manual
                else -> Unknown
            }
        }
    }
}

