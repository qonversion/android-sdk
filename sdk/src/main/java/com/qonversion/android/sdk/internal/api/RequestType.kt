package com.qonversion.android.sdk.internal.api

internal enum class RequestType {
    RemoteConfig,
    AttachUserToExperiment,
    DetachUserFromExperiment,
    Purchase,
    Restore,
    Attribution,
    GetProperties,
    EligibilityForProductIds,
    Identify,
}
