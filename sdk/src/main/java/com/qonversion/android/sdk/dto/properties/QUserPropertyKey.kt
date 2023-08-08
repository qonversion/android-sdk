package com.qonversion.android.sdk.dto.properties

enum class QUserPropertyKey(val userPropertyCode: String) {
    Email("_q_email"),
    Name("_q_name"),
    KochavaDeviceId("_q_kochava_device_id"),
    AppsFlyerUserId("_q_appsflyer_user_id"),
    AdjustAdId("_q_adjust_adid"),
    CustomUserId("_q_custom_user_id"),
    FacebookAttribution("_q_fb_attribution"),
    FirebaseAppInstanceId("_q_firebase_instance_id"),
    AppSetId("_q_app_set_id"),
    AdvertisingId("_q_advertising_id"), // iOS only
    Custom("");

    companion object {
        internal fun fromString(key: String) =
            values().find { it.userPropertyCode == key } ?: Custom
    }
}
