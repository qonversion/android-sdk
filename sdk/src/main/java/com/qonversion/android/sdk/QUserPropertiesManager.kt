package com.qonversion.android.sdk

import android.app.Application
import android.os.Handler
import android.os.HandlerThread
import com.qonversion.android.sdk.billing.secondsToMilliSeconds
import com.qonversion.android.sdk.logger.Logger
import com.qonversion.android.sdk.storage.PropertiesStorage
import javax.inject.Inject

class QUserPropertiesManager @Inject internal constructor(
    private val context: Application,
    private val repository: QonversionRepository,
    private var propertiesStorage: PropertiesStorage,
    private val calculator: IncrementalCalculator,
    private val logger: Logger
) {
    private var handler: Handler? = null
    private var isRequestInProgress: Boolean = false
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

            repository.sendProperties(properties,
                onSuccess = {
                    isRequestInProgress = false
                    retriesCounter = 0
                    retryDelay = PROPERTY_UPLOAD_MIN_DELAY
                    propertiesStorage.clear(properties)
                },
                onError = {
                    isRequestInProgress = false
                    retriesCounter++
                    retryDelay = calculator.countDelay(PROPERTY_UPLOAD_MIN_DELAY, retriesCounter)
                    sendPropertiesWithDelay(retryDelay)
                })
        }
    }

    fun setProperty(key: QUserProperties, value: String) {
        setUserProperty(key.userPropertyCode, value)
    }

    fun setUserID(value: String) {
        setUserProperty(QUserProperties.CustomUserId.userPropertyCode, value)
    }

    fun setUserProperty(key: String, value: String) {
        if (value.isEmpty()) {
            return
        }

        propertiesStorage.save(key, value)
        if (retriesCounter == 0) {
            sendPropertiesWithDelay(retryDelay)
        }
    }

    private fun sendPropertiesWithDelay(delaySec: Int) {
        val delayMillis = delaySec.toLong().secondsToMilliSeconds()

        handler?.postDelayed({
            forceSendProperties()
        }, delayMillis)
    }
}