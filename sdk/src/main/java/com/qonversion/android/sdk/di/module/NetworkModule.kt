package com.qonversion.android.sdk.di.module

import android.app.Application
import com.qonversion.android.sdk.BuildConfig
import com.qonversion.android.sdk.di.scope.ApplicationScope
import com.qonversion.android.sdk.dto.*
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

@Module
class NetworkModule {
    @ApplicationScope
    @Provides
    fun provideRetrofit(
        client: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        return Retrofit.Builder()
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .baseUrl(BASE_URL)
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
            .build()
    }

    @ApplicationScope
    @Provides
    fun provideOkHttpClient(context: Application): OkHttpClient {
        return OkHttpClient.Builder()
            .cache(Cache(context.cacheDir, CACHE_SIZE))
            .readTimeout(TIMEOUT, TimeUnit.SECONDS)
            .connectTimeout(TIMEOUT, TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.DEBUG) {
                    val logging = HttpLoggingInterceptor()
                    logging.level = HttpLoggingInterceptor.Level.BODY
                    addInterceptor(logging)
                }
            }
            .build()
    }

    companion object {
        private const val BASE_URL = "https://api.qonversion.io/"
        private const val TIMEOUT = 30L
        private const val CACHE_SIZE = 10485776L //10 MB
    }
}