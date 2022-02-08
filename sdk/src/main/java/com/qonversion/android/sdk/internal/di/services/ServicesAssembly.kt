package com.qonversion.android.sdk.internal.di.services

import com.qonversion.android.sdk.internal.di.misc.MiscAssembly
import com.qonversion.android.sdk.internal.user.UserService
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesService

internal interface ServicesAssembly {

    fun init(miscAssembly: MiscAssembly)

    val userPropertiesService: UserPropertiesService

    val userService: UserService
}
