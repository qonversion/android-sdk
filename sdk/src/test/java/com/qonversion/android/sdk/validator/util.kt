package com.qonversion.android.sdk.validator

import com.qonversion.android.sdk.dto.AttributionRequest
import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.ProviderData
import com.qonversion.android.sdk.dto.app.App
import com.qonversion.android.sdk.dto.device.AdsDto
import com.qonversion.android.sdk.dto.device.Device
import com.qonversion.android.sdk.dto.device.Os
import com.qonversion.android.sdk.dto.device.Screen

class Util {

    companion object {
        val QONVERSION_REQUEST_WITH_NOT_EMPTY_CLIENT_UID = AttributionRequest(
            d = Environment(
                internalUserID = "internal_user_id",
                app = App(
                    name = "app_name",
                    build = "app_build",
                    bundle = "app_bundle",
                    version = "app_version"
                ),
                device = Device(
                    os = Os(),
                    ads = AdsDto(true, "edfa"),
                    deviceId = "user_device_id",
                    model = "user_device_model",
                    carrier = "user_device_carrier",
                    locale = "user_device_locale",
                    screen = Screen("user_device_height","user_device_width"),
                    timezone = "user_device_timezone"
                )
            ),
            v = "version",
            accessToken = "user_access_token",
            clientUid = "user_client_uid",
            providerData = ProviderData(
                data = mutableMapOf(
                    "any_string_key" to "any_string_value",
                    "any_boolean_key" to true,
                    "any_int_key" to 100.toInt(),
                    "any_long_key" to Long.MAX_VALUE,
                    "any_double_key" to 1000000.0000.toDouble()
                ),
                provider = "provider_name",
                uid = "provider_uid"
            )
        )

        val QONVERSION_REQUEST_WITH_EMPTY_CLIENT_UID = AttributionRequest(
            d = Environment(
                internalUserID = "internal_user_id",
                app = App(
                    name = "app_name",
                    build = "app_build",
                    bundle = "app_bundle",
                    version = "app_version"
                ),
                device = Device(
                    os = Os(),
                    ads = AdsDto(true, "edfa"),
                    deviceId = "user_device_id",
                    model = "user_device_model",
                    carrier = "user_device_carrier",
                    locale = "user_device_locale",
                    screen = Screen("user_device_height","user_device_width"),
                    timezone = "user_device_timezone"
                )
            ),
            v = "version",
            accessToken = "user_access_token",
            clientUid = "",
            providerData = ProviderData(
                data = mutableMapOf(
                    "any_string_key" to "any_string_value",
                    "any_boolean_key" to true,
                    "any_int_key" to 100.toInt(),
                    "any_long_key" to Long.MAX_VALUE,
                    "any_double_key" to 1000000.0000.toDouble()
                ),
                provider = "provider_name",
                uid = "provider_uid"
            )
        )
    }
}