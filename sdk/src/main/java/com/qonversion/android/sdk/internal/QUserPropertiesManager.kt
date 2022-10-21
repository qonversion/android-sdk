package com.qonversion.android.sdk.internal

import android.app.Application
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.VisibleForTesting
import com.qonversion.android.sdk.dto.QUserProperties
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.QonversionError
import com.qonversion.android.sdk.QonversionErrorCode
import com.qonversion.android.sdk.listeners.QonversionLaunchCallback
import com.qonversion.android.sdk.internal.billing.secondsToMilliSeconds
import com.qonversion.android.sdk.dto.QLaunchResult
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.storage.PropertiesStorage
import javax.inject.Inject

internal class QUserPropertiesManager @Inject internal constructor(
    private val context: Application,
    private val repository: QonversionRepository,
    private var propertiesStorage: PropertiesStorage,
    private val delayCalculator: IncrementalDelayCalculator,
    private val logger: Logger
) {
    internal var productCenterManager: QProductCenterManager? = null
    private var handler: Handler? = null
    private var isRequestInProgress = false
    private var isSendingScheduled = false
    private var retryDelay = PROPERTY_UPLOAD_MIN_DELAY
    private var retriesCounter = 0

    companion object {
        private const val LOOPER_THREAD_NAME = "userPropertiesThread"
        private const val PROPERTY_UPLOAD_MIN_DELAY = 5
    }

    init {
        val thread = HandlerThread(LOOPER_THREAD_NAME)
        thread.start()
        handler = Handler(thread.looper)
    }

    fun onAppBackground() {
        forceSendProperties()
    }

    fun onAppForeground() {
        if (propertiesStorage.getProperties().isNotEmpty()) {
            sendPropertiesWithDelay(retryDelay)
        }
    }

    fun sendFacebookAttribution() {
        try {
            val fbAttributionId = FacebookAttribution().getAttributionId(context.contentResolver)
            if (fbAttributionId != null) {
                setUserProperty(
                    QUserProperties.FacebookAttribution.userPropertyCode,
                    fbAttributionId
                )
            }
        } catch (e: IllegalStateException) {
            logger.release("Failed to retrieve facebook attribution ${e.localizedMessage}")
        }
    }

    fun forceSendProperties() {
        if (isRequestInProgress) {
            return
        }

        val properties = propertiesStorage.getProperties()

        if (properties.isNotEmpty()) {
            isRequestInProgress = true
            isSendingScheduled = false

            repository.sendProperties(properties,
                onSuccess = {
                    isRequestInProgress = false
                    retriesCounter = 0
                    retryDelay = PROPERTY_UPLOAD_MIN_DELAY
                    propertiesStorage.clear(properties)
                },
                onError = {
                    isRequestInProgress = false

                    if (it?.code === QonversionErrorCode.InvalidClientUid) {
                        productCenterManager?.launch(callback = object : QonversionLaunchCallback {
                            override fun onSuccess(launchResult: QLaunchResult) {
                                retryPropertiesRequest()
                            }

                            override fun onError(error: QonversionError) {
                                retryPropertiesRequest()
                            }
                        })
                    } else {
                        retryPropertiesRequest()
                    }
                })
        }
    }

    private fun retryPropertiesRequest() {
        retriesCounter++
        try {
            retryDelay =
                delayCalculator.countDelay(PROPERTY_UPLOAD_MIN_DELAY, retriesCounter)
            sendPropertiesWithDelay(retryDelay)
        } catch (e: IllegalArgumentException) {
            logger.debug("The error occurred during send properties. $e")
        }
    }

    fun setProperty(key: QUserProperties, value: String) {
        setUserProperty(key.userPropertyCode, value)
    }

    fun setUserProperty(key: String, value: String) {
        if (value.isEmpty()) {
            return
        }

        propertiesStorage.save(key, value)
        if (!isSendingScheduled) {
            sendPropertiesWithDelay(retryDelay)
        }
    }

    @VisibleForTesting
    fun sendPropertiesWithDelay(delaySec: Int) {
        if (Qonversion.appState.isBackground()) {
            return
        }

        val delayMillis = delaySec.toLong().secondsToMilliSeconds()
        isSendingScheduled = true
        handler?.postDelayed({
            forceSendProperties()
        }, delayMillis)
    }
}
