package com.qonversion.android.sdk.internal.di.storage

import com.qonversion.android.sdk.internal.common.localStorage.LocalStorage
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesStorage

internal interface StorageAssembly {

    val sharedPreferencesStorage: LocalStorage

    val sentUserPropertiesStorage: UserPropertiesStorage

    val pendingUserPropertiesStorage: UserPropertiesStorage
}
