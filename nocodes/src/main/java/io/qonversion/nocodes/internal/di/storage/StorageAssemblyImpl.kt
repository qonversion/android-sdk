package io.qonversion.nocodes.internal.di.storage

import android.content.Context
import android.content.SharedPreferences
import io.qonversion.nocodes.internal.common.PREFS_NAME
import io.qonversion.nocodes.internal.common.localStorage.LocalStorage
import io.qonversion.nocodes.internal.common.localStorage.SharedPreferencesStorage

internal class StorageAssemblyImpl(
    private val context: Context
) : StorageAssembly {
    override fun sharedPreferences(): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun sharedPreferencesStorage(): LocalStorage =
        SharedPreferencesStorage(sharedPreferences())
}
