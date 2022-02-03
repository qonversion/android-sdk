package com.qonversion.android.sdk.internal.userProperties.controller

import androidx.annotation.VisibleForTesting
import com.qonversion.android.sdk.internal.common.BaseClass
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesStorage
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesService
import com.qonversion.android.sdk.internal.utils.workers.DelayedWorker

private const val SENDING_DELAY_MS = 5000L
private const val KEY_REGEX = """(?=.*[a-zA-Z])^[-a-zA-Z0-9_.:]+$"""

internal class UserPropertiesControllerImpl(
    private val pendingPropertiesStorage: UserPropertiesStorage,
    private val sentPropertiesStorage: UserPropertiesStorage,
    private val service: UserPropertiesService,
    private val worker: DelayedWorker,
    private val sendingDelayMs: Long = SENDING_DELAY_MS,
    logger: Logger
) : BaseClass(logger), UserPropertiesController {

    override fun setProperty(key: String, value: String) {
        if (shouldSendProperty(key, value)) {
            pendingPropertiesStorage.add(key, value)
        }
        sendUserPropertiesIfNeeded()
    }

    override fun setProperties(properties: Map<String, String>) {
        val validatedProperties = properties.filter {
            shouldSendProperty(it.key, it.value)
        }
        pendingPropertiesStorage.add(validatedProperties)
        sendUserPropertiesIfNeeded()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun sendUserPropertiesIfNeeded(ignoreExistingJob: Boolean = false) {
        if (pendingPropertiesStorage.properties.isNotEmpty()) {
            worker.doDelayed(sendingDelayMs, ignoreExistingJob) {
                sendUserProperties()
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    suspend fun sendUserProperties() {
        try {
            val propertiesToSend = pendingPropertiesStorage.properties.toMap()
            if (propertiesToSend.isEmpty()) {
                return
            }
            val processedPropertiesKeys: List<String> = service.sendProperties(propertiesToSend)
            // We delete all sent properties even if they were not successfully handled
            // to prevent spamming api with unacceptable properties.

            val nonProcessedProperties = propertiesToSend - processedPropertiesKeys
            val processedProperties = propertiesToSend - nonProcessedProperties.keys

            pendingPropertiesStorage.delete(propertiesToSend)

            sentPropertiesStorage.add(processedProperties)

            nonProcessedProperties.takeIf { it.isNotEmpty() }?.let {
                logger.warn("Some user properties were not processed: ${it.keys.joinToString(", ")}.")
            }

            sendUserPropertiesIfNeeded(ignoreExistingJob = true)
        } catch (e: QonversionException) {
            logger.error("Failed to send user properties to api", e)
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun shouldSendProperty(key: String, value: String): Boolean {
        var shouldSend = true
        if (!isValidKey(key)) {
            shouldSend = false
            logger.error(
                """Invalid key "$key" for user property. 
                    |The key should be nonempty and may consist of letters A-Za-z, 
                    |numbers, and symbols _.:-.""".trimMargin())
        }
        if (!isValidValue(value)) {
            shouldSend = false
            logger.error("""The empty value provided for user property "$key".""")
        }

        if (shouldSend && sentPropertiesStorage.properties[key] == value) {
            shouldSend = false
            logger.info("""The same property with key: "$key" and value: "$value" 
                |was already sent for the current user. 
                |To avoid any confusion, it will not be sent again.""".trimMargin())
        }

        return shouldSend
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun isValidKey(key: String): Boolean {
        val regex = KEY_REGEX.toRegex()
        return key.isNotBlank() && regex.matches(key)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun isValidValue(value: String): Boolean = value.isNotEmpty()
}
