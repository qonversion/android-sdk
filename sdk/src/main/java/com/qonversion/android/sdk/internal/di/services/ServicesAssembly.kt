package com.qonversion.android.sdk.internal.di.services

import com.qonversion.android.sdk.internal.user.UserService
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesService

internal interface ServicesAssembly {

    val userPropertiesService: UserPropertiesService

    val userService: UserService
}
