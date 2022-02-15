package com.qonversion.android.sdk.internal.di.cacher

import com.qonversion.android.sdk.dto.User
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.cache.Cacher
import com.qonversion.android.sdk.internal.cache.CacherImpl
import com.qonversion.android.sdk.internal.common.StorageConstants
import com.qonversion.android.sdk.internal.di.mappers.MappersAssembly
import com.qonversion.android.sdk.internal.di.misc.MiscAssembly
import com.qonversion.android.sdk.internal.di.storage.StorageAssembly

internal class CacherAssemblyImpl(
    private val storageAssembly: StorageAssembly,
    private val mappersAssembly: MappersAssembly,
    private val miscAssembly: MiscAssembly,
    private val internalConfig: InternalConfig
) : CacherAssembly {
    override fun userCacher(): Cacher<User?> =
        CacherImpl(
            StorageConstants.UserInfo.key,
            storageAssembly.userDataProvider(),
            mappersAssembly.userCacheMapper(),
            storageAssembly.sharedPreferencesStorage(),
            miscAssembly.appLifecycleObserver(),
            internalConfig,
            miscAssembly.logger()
        )
}
