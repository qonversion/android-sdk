package com.qonversion.android.sdk

import android.app.Application
import android.os.Handler
import android.os.HandlerThread
import com.qonversion.android.sdk.logger.Logger
import com.qonversion.android.sdk.storage.PropertiesStorage
import com.qonversion.android.sdk.storage.SharedPreferencesCache
import javax.inject.Inject

class QUserPropertiesManager @Inject internal constructor(
    context: Application,
    private val repository: QonversionRepository,
    private var propertiesStorage: PropertiesStorage,
    logger: Logger
) {
    private var isRequestInProgress: Boolean = false
    private var mainHandler: Handler? = null

    companion object {
        private const val PROPERTY_UPLOAD_PERIOD = 5 * 1000
        private const val LOOPER_THREAD_NAME = "userPropertiesThread"
    }

    init {
        try {
            val fbAttributionId = FacebookAttribution().getAttributionId(context.contentResolver)
            if (fbAttributionId != null) {
                propertiesStorage.save(QUserProperties.FacebookAttribution.userPropertyCode, fbAttributionId)
            }

            val thread = HandlerThread(LOOPER_THREAD_NAME)
            thread.start()
            mainHandler = Handler(thread.looper)

            sendPropertiesAtPeriod()
        } catch (e: IllegalStateException) {
            logger.release("Failed to retrieve facebook attribution ${e.localizedMessage}")
        }
    }

    private fun sendPropertiesAtPeriod() {
        if (isRequestInProgress) {
            return
        }

        mainHandler?.postDelayed({
            forceSendProperties()
        }, PROPERTY_UPLOAD_PERIOD.toLong())
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
}