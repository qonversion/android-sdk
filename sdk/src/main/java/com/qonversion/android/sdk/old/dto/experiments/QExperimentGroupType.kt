package com.qonversion.android.sdk.old.dto.experiments

enum class QExperimentGroupType(val type: Int) {
    A(0),
    B(1);

    companion object {
        fun fromType(type: Int): QExperimentGroupType {
            return when (type) {
                1 -> B
                else -> A
            }
        }
    }
}
