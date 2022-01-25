package com.qonversion.android.sdk.dto

/**
 * This enum class represents all defined user property values
 * that can be assigned to the user. Provide these keys along with values
 * to [Qonversion.setUserProperty] method.
 */
enum class UserProperty(val code: String) {
    Email("_q_email"),
    Name("_q_name"),
    KochavaDeviceId("_q_kochava_device_id"),
    AppsFlyerUserId("_q_appsflyer_user_id"),
    AdjustAdId("_q_adjust_adid"),
    CustomUserId("_q_custom_user_id"),
    FacebookAttribution("_q_fb_attribution");
}
