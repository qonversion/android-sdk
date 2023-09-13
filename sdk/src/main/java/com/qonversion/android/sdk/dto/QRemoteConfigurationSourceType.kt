package com.qonversion.android.sdk.dto

enum class QRemoteConfigurationSourceType(val type: String) {
    ExperimentControlGroup("experiment_control_group"),
    ExperimentTreatmentGroup("experiment_treatment_group"),
    RemoteConfiguration("remote_configuration"),
    Unknown("unknown");

    companion object {
        fun fromType(type: String): QRemoteConfigurationSourceType {
            return when (type) {
                "experiment_control_group" -> ExperimentControlGroup
                "experiment_treatment_group" -> ExperimentTreatmentGroup
                "remote_configuration" -> RemoteConfiguration
                else -> Unknown
            }
        }
    }
}
