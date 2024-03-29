package com.qonversion.android.sdk.internal

import android.app.Application
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.VisibleForTesting
import com.qonversion.android.sdk.dto.properties.QUserPropertyKey
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.QonversionErrorCode
import com.qonversion.android.sdk.dto.properties.QUserProperties
import com.qonversion.android.sdk.listeners.QonversionLaunchCallback
import com.qonversion.android.sdk.internal.dto.QLaunchResult
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.provider.AppStateProvider
import com.qonversion.android.sdk.internal.repository.QRepository
import com.qonversion.android.sdk.internal.storage.PropertiesStorage
import com.qonversion.android.sdk.listeners.QonversionUserPropertiesCallback
import javax.inject.Inject

internal class QUserPropertiesManager @Inject internal constructor(
    private val context: Application,
    private val repository: QRepository,
    private var propertiesStorage: PropertiesStorage,
    private val delayCalculator: IncrementalDelayCalculator,
    private val appStateProvider: AppStateProvider,
    private val logger: Logger
) : FacebookAttributionListener {
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
            FacebookAttribution().getAttributionId(context.contentResolver, this)
        } catch (e: IllegalStateException) {
            logger.release("Failed to retrieve facebook attribution ${e.localizedMessage}")
        }
    }

    override fun onFbAttributionIdResult(id: String?) {
        id ?: return

        setCustomUserProperty(QUserPropertyKey.FacebookAttribution.userPropertyCode, id)
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
                onSuccess = { result ->
                    result.propertyErrors.forEach { propertyError ->
                        logger.release("Failed to save property ${propertyError.key}: ${propertyError.error}")
                    }

                    isRequestInProgress = false
                    retriesCounter = 0
                    retryDelay = PROPERTY_UPLOAD_MIN_DELAY

                    // Cleaning all the properties (not only succeeded) as we don't want to resend invalid ones again
                    propertiesStorage.clear(properties)
                },
                onError = {
                    isRequestInProgress = false

                    if (it.code === QonversionErrorCode.InvalidClientUid) {
                        productCenterManager?.launch(callback = object : QonversionLaunchCallback {
                            override fun onSuccess(launchResult: QLaunchResult) {
                                retryPropertiesRequest()
                            }

                            override fun onError(error: QonversionError, httpCode: Int?) {
                                retryPropertiesRequest()
                            }
                        })
                    } else {
                        retryPropertiesRequest()
                    }
                })
        }
    }

    @VisibleForTesting
    fun retryPropertiesRequest() {
        retriesCounter++
        try {
            retryDelay =
                delayCalculator.countDelay(PROPERTY_UPLOAD_MIN_DELAY, retriesCounter)
            sendPropertiesWithDelay(retryDelay)
        } catch (e: IllegalArgumentException) {
            logger.debug("The error occurred during send properties. $e")
        }
    }

    fun setUserProperty(key: QUserPropertyKey, value: String) {
        if (key === QUserPropertyKey.Custom) {
            logger.release("Can not set user property with the key `QUserPropertyKey.Custom`. " +
                    "To set custom user property, use the `setCustomUserProperty` method.")
            return
        }
        setCustomUserProperty(key.userPropertyCode, value)
    }

    fun setCustomUserProperty(key: String, value: String) {
        if (value.isEmpty()) {
            return
        }

        propertiesStorage.save(key, value)
        if (!isSendingScheduled) {
            sendPropertiesWithDelay(retryDelay)
        }
    }

    fun userProperties(callback: QonversionUserPropertiesCallback) {
        repository.getProperties(
            onSuccess = { properties -> callback.onSuccess(QUserProperties(properties)) },
            onError = { error -> callback.onError(error) }
        )
    }

    @VisibleForTesting
    fun sendPropertiesWithDelay(delaySec: Int) {
        if (appStateProvider.appState.isBackground()) {
            return
        }

        val delayMillis = delaySec.toLong().secondsToMilliSeconds()
        isSendingScheduled = true
        handler?.postDelayed({
            forceSendProperties()
        }, delayMillis)
    }
}
