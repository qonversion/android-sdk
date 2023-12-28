package com.qonversion.android.sdk.internal.di.module

import android.app.Application
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.api.ApiHeadersProvider
import com.qonversion.android.sdk.internal.api.ApiHelper
import com.qonversion.android.sdk.internal.api.NetworkInterceptor
import com.qonversion.android.sdk.internal.api.RateLimiter
import com.qonversion.android.sdk.internal.di.scope.ApplicationScope
import com.qonversion.android.sdk.internal.dto.QDateAdapter
import com.qonversion.android.sdk.internal.dto.QEligibilityAdapter
import com.qonversion.android.sdk.internal.dto.QEligibilityStatusAdapter
import com.qonversion.android.sdk.internal.dto.QEntitlementGrantTypeAdapter
import com.qonversion.android.sdk.internal.dto.QExperimentGroupTypeAdapter
import com.qonversion.android.sdk.internal.dto.QOfferingAdapter
import com.qonversion.android.sdk.internal.dto.QOfferingTagAdapter
import com.qonversion.android.sdk.internal.dto.QOfferingsAdapter
import com.qonversion.android.sdk.internal.dto.QEntitlementSourceAdapter
import com.qonversion.android.sdk.internal.dto.QPermissionsAdapter
import com.qonversion.android.sdk.internal.dto.QProductDurationAdapter
import com.qonversion.android.sdk.internal.dto.QProductRenewStateAdapter
import com.qonversion.android.sdk.internal.dto.QProductTypeAdapter
import com.qonversion.android.sdk.internal.dto.QProductsAdapter
import com.qonversion.android.sdk.internal.dto.QRemoteConfigurationSourceAssignmentTypeAdapter
import com.qonversion.android.sdk.internal.dto.QRemoteConfigurationSourceTypeAdapter
import com.qonversion.android.sdk.internal.dto.QTransactionEnvironmentAdapter
import com.qonversion.android.sdk.internal.dto.QTransactionOwnershipTypeAdapter
import com.qonversion.android.sdk.internal.dto.QTransactionTypeAdapter
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

@Module
internal class NetworkModule {
    @ApplicationScope
    @Provides
    fun provideRetrofit(
        client: OkHttpClient,
        moshi: Moshi,
        internalConfig: InternalConfig
    ): Retrofit {
        return Retrofit.Builder()
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .baseUrl(internalConfig.apiUrl)
            .client(client)
            .build()
    }

    @ApplicationScope
    @Provides
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(QProductDurationAdapter())
            .add(QDateAdapter())
            .add(QProductsAdapter())
            .add(QPermissionsAdapter())
            .add(QProductTypeAdapter())
            .add(QProductRenewStateAdapter())
            .add(QEntitlementSourceAdapter())
            .add(QOfferingsAdapter())
            .add(QOfferingAdapter())
            .add(QOfferingTagAdapter())
            .add(QExperimentGroupTypeAdapter())
            .add(QRemoteConfigurationSourceTypeAdapter())
            .add(QRemoteConfigurationSourceAssignmentTypeAdapter())
            .add(QEligibilityStatusAdapter())
            .add(QEligibilityAdapter())
            .add(QTransactionOwnershipTypeAdapter())
            .add(QTransactionTypeAdapter())
            .add(QTransactionEnvironmentAdapter())
            .add(QEntitlementGrantTypeAdapter())
            .build()
    }

    @ApplicationScope
    @Provides
    fun provideOkHttpClient(
        context: Application,
        interceptor: NetworkInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .cache(Cache(context.cacheDir, CACHE_SIZE))
            .readTimeout(TIMEOUT, TimeUnit.SECONDS)
            .connectTimeout(TIMEOUT, TimeUnit.SECONDS)
            .addInterceptor(interceptor)
            .build()
    }

    @ApplicationScope
    @Provides
    fun provideHeadersInterceptor(
        apiHeadersProvider: ApiHeadersProvider,
        config: InternalConfig,
        apiHelper: ApiHelper
    ): NetworkInterceptor {
        return NetworkInterceptor(apiHeadersProvider, apiHelper, config)
    }

    @ApplicationScope
    @Provides
    fun provideApiHelper(
        internalConfig: InternalConfig
    ): ApiHelper {
        return ApiHelper(internalConfig.apiUrl)
    }

    @ApplicationScope
    @Provides
    fun provideRateLimiter(): RateLimiter {
        return RateLimiter(MAX_SIMILAR_API_REQUESTS_PER_SECOND)
    }

    companion object {
        private const val TIMEOUT = 30L
        private const val CACHE_SIZE = 10485776L // 10 MB
        private const val MAX_SIMILAR_API_REQUESTS_PER_SECOND = 5
    }
}
