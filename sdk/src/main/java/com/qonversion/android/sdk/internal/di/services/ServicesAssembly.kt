package com.qonversion.android.sdk.internal.di.services

import com.qonversion.android.sdk.internal.user.service.UserService
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesService

internal interface ServicesAssembly {

    fun userPropertiesService(): UserPropertiesService

    fun userService(): UserService
}
