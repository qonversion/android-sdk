package com.qonversion.android.sdk.internal.userProperties.controller

import androidx.annotation.VisibleForTesting
import com.qonversion.android.sdk.internal.common.BaseClass
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesStorage
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesService
import com.qonversion.android.sdk.internal.utils.workers.DelayedWorker

private const val SENDING_DELAY_MS = 5000L

internal class UserPropertiesControllerImpl(
    private val storage: UserPropertiesStorage,
    private val service: UserPropertiesService,
    private val worker: DelayedWorker,
    private val sendingDelayMs: Long = SENDING_DELAY_MS,
    logger: Logger
) : BaseClass(logger), UserPropertiesController {

    override fun setProperty(key: String, value: String) {
        if (isValidUserProperty(key, value)) {
            storage.add(key, value)
        }
        sendUserPropertiesIfNeeded()
    }

    override fun setProperties(properties: Map<String, String>) {
        val validatedProperties = properties.filter {
            isValidUserProperty(it.key, it.value)
        }
        storage.add(validatedProperties)
        sendUserPropertiesIfNeeded()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun sendUserPropertiesIfNeeded() {
        if (storage.properties.isNotEmpty()) {
            worker.doDelayed(sendingDelayMs) {
                sendUserProperties()
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    suspend fun sendUserProperties() {
        try {
            val propertiesToSend = storage.properties.toMap()
            val processedProperties = service.sendProperties(propertiesToSend)
            // We delete all sent properties even if they were not successfully handled
            // to prevent spamming api with unacceptable properties.
            storage.delete(propertiesToSend)
            sendUserPropertiesIfNeeded()

            propertiesToSend.keys.filter {
                !processedProperties.contains(it)
            }.takeIf { it.isNotEmpty() }?.let {
                logger.warn("Some user properties were not processed: ${it.joinToString(", ")}.")
            }
        } catch (e: QonversionException) {
            logger.error("Failed to send user properties to api", e)
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun isValidUserProperty(key: String, value: String): Boolean {
        var isValid = true
        if (!isValidKey(key)) {
            isValid = false
            logger.error(
                """Invalid key "$key" for user property. 
                    |The key should be nonempty and may consist of letters A-Za-z, 
                    |numbers, and symbols _.:-.""".trimMargin())
        }
        if (!isValidValue(value)) {
            isValid = false
            logger.error("""The empty value provided for user property "$key".""")
        }

        return isValid
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun isValidKey(key: String): Boolean {
        val regex = """(?=.*[a-zA-Z])^[-a-zA-Z0-9_.:]+$""".toRegex()
        return key.isNotBlank() && regex.matches(key)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun isValidValue(value: String): Boolean = value.isNotEmpty()
}
