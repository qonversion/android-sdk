package io.qonversion.nocodes.internal.networkLayer.headerBuilder

import android.os.Build
import io.qonversion.nocodes.internal.common.PLATFORM
import io.qonversion.nocodes.internal.common.StorageConstants
import io.qonversion.nocodes.internal.common.localStorage.LocalStorage
import io.qonversion.nocodes.internal.networkLayer.ApiHeader
import io.qonversion.nocodes.internal.provider.PrimaryConfigProvider
import java.util.Locale

internal class HeaderBuilderImpl(
    private val localStorage: LocalStorage,
    private val locale: Locale,
    private val primaryConfigProvider: PrimaryConfigProvider
) : HeaderBuilder {

    private val source by lazy {
        localStorage.getString(StorageConstants.SourceKey.key, PLATFORM) ?: PLATFORM
    }

    private val sourceVersion by lazy {
        localStorage.getString(
            StorageConstants.VersionKey.key,
            primaryConfigProvider.primaryConfig.sdkVersion
        )
            ?: primaryConfigProvider.primaryConfig.sdkVersion
    }

    override fun buildCommonHeaders(): Map<String, String> {
        val locale = locale.language
        val projectKey = primaryConfigProvider.primaryConfig.projectKey
        val bearer = "Bearer $projectKey"

        return mapOf(
            ApiHeader.Authorization.key to bearer,
            ApiHeader.Locale.key to locale,
            ApiHeader.Source.key to source,
            ApiHeader.SourceVersion.key to sourceVersion,
            ApiHeader.Platform.key to PLATFORM,
            ApiHeader.PlatformVersion.key to Build.VERSION.RELEASE
        )
    }
}
