package com.qonversion.android.sdk.internal.api

// Represents the initial trigger for the API request
internal enum class RequestTrigger(val key: String) {
    Background("Background"),
    Init("Init"),
    Identify("Identify"),
    Products("Products"),
    Purchase("Purchase"),
    UserProperties("UserProperties"),
    Restore("Restore"),
    SyncHistoricalData("SyncHistoricalData"),
    SyncPurchases("SyncPurchases"),
    ActualizePermissions("ActualizePermissions"),
    Logout("Logout"),
}
