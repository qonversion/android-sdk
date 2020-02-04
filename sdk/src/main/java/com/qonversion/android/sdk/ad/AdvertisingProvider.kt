package com.qonversion.android.sdk.ad

import android.app.Application
import androidx.ads.identifier.AdvertisingIdClient
import androidx.ads.identifier.AdvertisingIdInfo
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures.addCallback
import java.lang.IllegalStateException
import java.util.concurrent.Executors

class AdvertisingProvider {

    interface Callback {
        fun onSuccess(advertisingId: String, provider: String)
        fun onFailure(t: Throwable)
    }

    fun init(context: Application, callback: Callback) {
        if (AdvertisingIdClient.isAdvertisingIdProviderAvailable(context)) {
            val advertisingIdInfoListenableFuture =
                AdvertisingIdClient.getAdvertisingIdInfo(context.applicationContext)

            addCallback(advertisingIdInfoListenableFuture,
                object : FutureCallback<AdvertisingIdInfo> {
                    override fun onSuccess(adInfo: AdvertisingIdInfo?) {
                        val id: String? = adInfo?.id
                        val providerPackageName: String? = adInfo?.providerPackageName
                        if (id != null && providerPackageName != null) {
                            callback.onSuccess(
                                advertisingId = id,
                                provider = providerPackageName
                            )
                        }
                    }

                    override fun onFailure(t: Throwable) {
                        // Try to connect to the Advertising ID provider again, or fall
                        // back to an ads solution that doesn't require using the
                        // Advertising ID library.
                        callback.onFailure(t)
                    }
                }, Executors.newSingleThreadExecutor())
        } else {
            callback.onFailure(
                IllegalStateException("AdvertisingIdClient.isAdvertisingIdProviderAvailable is FALSE")
            )
        }
    }
}