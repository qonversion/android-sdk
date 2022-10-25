package com.qonversion.android.sdk.listeners

import com.qonversion.android.sdk.internal.dto.QPermission

interface UpdatedPurchasesListener {

    fun onPermissionsUpdate(permissions: Map<String, QPermission>)
}
