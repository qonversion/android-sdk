package com.qonversion.android.sdk

import android.app.Application
import android.os.Handler
import android.os.HandlerThread
import com.qonversion.android.sdk.logger.Logger
import com.qonversion.android.sdk.storage.PropertiesStorage
import javax.inject.Inject

class QUserPropertiesManager @Inject internal constructor(
    private val context: Application,
    private val repository: QonversionRepository,
    private var propertiesStorage: PropertiesStorage,
    private val logger: Logger
) {
    private var isRequestInProgress: Boolean = false
    private var handler: Handler? = null

    companion object {
        private const val PROPERTY_UPLOAD_PERIOD = 5 * 1000
        private const val LOOPER_THREAD_NAME = "userPropertiesThread"
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
                    propertiesStorage.clear(properties)
                },
                onError = {
                    isRequestInProgress = false
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
        sendPropertiesAtPeriod()
    }

    private fun sendPropertiesAtPeriod() {
        if (isRequestInProgress) {
            return
        }

        handler?.postDelayed({
            forceSendProperties()
        }, PROPERTY_UPLOAD_PERIOD.toLong())
    }
}