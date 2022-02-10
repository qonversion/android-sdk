package com.qonversion.android.sdk.internal.di.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import com.qonversion.android.sdk.internal.common.PREFS_NAME
import com.qonversion.android.sdk.internal.common.StorageConstants
import com.qonversion.android.sdk.internal.common.localStorage.LocalStorage
import com.qonversion.android.sdk.internal.common.localStorage.SharedPreferencesStorage
import com.qonversion.android.sdk.internal.di.mappers.MappersAssembly
import com.qonversion.android.sdk.internal.di.misc.MiscAssembly
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesStorage
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesStorageImpl

internal class StorageAssemblyImpl(
    private val mappersAssembly: MappersAssembly,
    private val miscAssembly: MiscAssembly
) : StorageAssembly {
    override val sharedPreferences: SharedPreferences
        get() = miscAssembly.application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override val sharedPreferencesStorage: LocalStorage
        get() = SharedPreferencesStorage(sharedPreferences)

    override val sentUserPropertiesStorage: UserPropertiesStorage
        get() = providePropertiesStorage(StorageConstants.SentUserProperties.key)

    override val pendingUserPropertiesStorage: UserPropertiesStorage
        get() = providePropertiesStorage(StorageConstants.PendingUserProperties.key)

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun providePropertiesStorage(storageName: String): UserPropertiesStorage {
        return UserPropertiesStorageImpl(
            sharedPreferencesStorage,
            mappersAssembly.mapDataMapper,
            storageName,
            miscAssembly.logger
        )
    }
}
