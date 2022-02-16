package com.qonversion.android.sdk.internal.di.storage

import android.content.SharedPreferences
import com.qonversion.android.sdk.internal.common.localStorage.LocalStorage
import com.qonversion.android.sdk.internal.user.storage.UserDataProvider
import com.qonversion.android.sdk.internal.user.storage.UserDataStorage
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesStorage

internal interface StorageAssembly {

    fun sharedPreferences(): SharedPreferences

    fun sharedPreferencesStorage(): LocalStorage

    fun sentUserPropertiesStorage(): UserPropertiesStorage

    fun pendingUserPropertiesStorage(): UserPropertiesStorage

    fun userDataProvider(): UserDataProvider

    fun userDataStorage(): UserDataStorage
}
