package com.qonversion.android.sdk.internal.api

// Represents the initial trigger for the API request
internal enum class RequestTrigger(val key: String) {
    Purchase("Purchase"),
    Restore("Restore"),
    SyncHistoricalData("SyncHistoricalData"),
    SyncPurchases("SyncPurchases"),
}
