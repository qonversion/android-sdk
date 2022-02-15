package com.qonversion.android.sdk.internal.di.cacher

import com.qonversion.android.sdk.dto.User
import com.qonversion.android.sdk.internal.cache.Cacher

internal interface CacherAssembly {

    fun userCacher(): Cacher<User?>
}
