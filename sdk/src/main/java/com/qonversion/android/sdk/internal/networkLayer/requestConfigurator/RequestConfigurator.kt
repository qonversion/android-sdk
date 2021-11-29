package com.qonversion.android.sdk.internal.networkLayer.requestConfigurator

import com.qonversion.android.sdk.internal.networkLayer.dto.Request

interface RequestConfigurator {

    fun configureUserRequest(id: String): Request

    fun configureCreateUserRequest(id: String): Request
}
