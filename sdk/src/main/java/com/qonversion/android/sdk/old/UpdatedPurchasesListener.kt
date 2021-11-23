package com.qonversion.android.sdk.old

import com.qonversion.android.sdk.old.dto.QPermission

public interface UpdatedPurchasesListener {

    fun onPermissionsUpdate(permissions: Map<String, QPermission>)
}
