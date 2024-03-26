package com.qonversion.android.sdk.internal.api

internal enum class RequestType {
    Init,
    RemoteConfig,
    RemoteConfigList,
    AttachUserToExperiment,
    DetachUserFromExperiment,
    Purchase,
    Restore,
    Attribution,
    GetProperties,
    EligibilityForProductIds,
    Identify,
    AttachUserToRemoteConfiguration,
    DetachUserFromRemoteConfiguration,
}
