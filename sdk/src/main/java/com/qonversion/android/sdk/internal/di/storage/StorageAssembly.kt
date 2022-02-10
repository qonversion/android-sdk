package com.qonversion.android.sdk.internal.di.storage

import android.content.SharedPreferences
import com.qonversion.android.sdk.internal.common.localStorage.LocalStorage
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesStorage

internal interface StorageAssembly {

    val sharedPreferences: SharedPreferences

    val sharedPreferencesStorage: LocalStorage

    val sentUserPropertiesStorage: UserPropertiesStorage

    val pendingUserPropertiesStorage: UserPropertiesStorage
}
