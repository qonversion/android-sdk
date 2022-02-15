package com.qonversion.android.sdk.internal.di.storage

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import com.qonversion.android.sdk.internal.common.PREFS_NAME
import com.qonversion.android.sdk.internal.common.StorageConstants
import com.qonversion.android.sdk.internal.common.localStorage.LocalStorage
import com.qonversion.android.sdk.internal.common.localStorage.SharedPreferencesStorage
import com.qonversion.android.sdk.internal.di.mappers.MappersAssembly
import com.qonversion.android.sdk.internal.di.misc.MiscAssembly
import com.qonversion.android.sdk.internal.user.storage.UserDataProvider
import com.qonversion.android.sdk.internal.user.storage.UserDataStorage
import com.qonversion.android.sdk.internal.user.storage.UserDataStorageImpl
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesStorage
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesStorageImpl

internal class StorageAssemblyImpl(
    private val application: Application,
    private val mappersAssembly: MappersAssembly,
    private val miscAssembly: MiscAssembly
) : StorageAssembly {
    override fun sharedPreferences(): SharedPreferences =
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun sharedPreferencesStorage(): LocalStorage =
        SharedPreferencesStorage(sharedPreferences())

    override fun sentUserPropertiesStorage(): UserPropertiesStorage =
        userPropertiesStorage(StorageConstants.SentUserProperties.key)

    override fun pendingUserPropertiesStorage(): UserPropertiesStorage =
        userPropertiesStorage(StorageConstants.PendingUserProperties.key)

    override fun userDataProvider(): UserDataProvider = userDataStorage()

    override fun userDataStorage(): UserDataStorage =
        UserDataStorageImpl(sharedPreferencesStorage())

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun userPropertiesStorage(storageName: String): UserPropertiesStorage {
        return UserPropertiesStorageImpl(
            sharedPreferencesStorage(),
            mappersAssembly.mapDataMapper(),
            storageName,
            miscAssembly.logger()
        )
    }
}
