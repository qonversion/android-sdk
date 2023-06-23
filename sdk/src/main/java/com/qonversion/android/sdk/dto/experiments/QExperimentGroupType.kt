package com.qonversion.android.sdk.dto.experiments

enum class QExperimentGroupType(val type: String) {
    Control("control"),
    Treatment("treatment"),
    Unknown("unknown");

    companion object {
        fun fromType(type: String): QExperimentGroupType {
            return when (type) {
                "control" -> Control
                "treatment" -> Treatment
                else -> Unknown
            }
        }
    }
}
