package io.qonversion.nocodes.internal.di.storage

import android.content.SharedPreferences
import io.qonversion.nocodes.internal.common.localStorage.LocalStorage

internal interface StorageAssembly {

    fun sharedPreferences(): SharedPreferences

    fun sharedPreferencesStorage(): LocalStorage
}
