package com.qonversion.android.sdk.purchasequeue

import com.qonversion.android.sdk.EnvironmentProvider
import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.app.App
import com.qonversion.android.sdk.dto.device.AdsDto
import com.qonversion.android.sdk.dto.device.Device
import com.qonversion.android.sdk.dto.device.Os
import com.qonversion.android.sdk.dto.device.Screen

class MockEnvironmentProvider: EnvironmentProvider {
    override fun getInfo(internalUserId: String, ads: AdsDto): Environment {
        return Environment(
            internalUserID = internalUserId,
            app = App(
                name = "test_app_name",
                build = "test_app_build",
                bundle = "test_app_bundle",
                version = "test_app_version"
            ),
            device = Device(
                os = Os(
                    name = "test_os_name",
                    version = "test_os_version"
                ),
                ads = ads,
                carrier = "test_carrier",
                deviceId = "test_device_id",
                locale = "test_locale",
                model = "test_model",
                screen = Screen(
                    width = "test_width",
                    height = "test_height"
                ),
                timezone = "test_timezone"
            )
        )
    }
}