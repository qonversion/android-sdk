package com.qonversion.android.sdk

import com.qonversion.android.sdk.dto.QPermission

interface UpdatedPurchasesListener {

    fun onPermissionsUpdate(permissions: Map<String, QPermission>)
}
