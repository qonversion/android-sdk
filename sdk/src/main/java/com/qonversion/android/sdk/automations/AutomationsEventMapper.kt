package com.qonversion.android.sdk.automations

import com.qonversion.android.sdk.billing.getCurrentTimeInMillis
import com.qonversion.android.sdk.billing.secondsToMilliSeconds
import com.qonversion.android.sdk.logger.Logger
import org.json.JSONException
import org.json.JSONObject
import java.util.Date

class AutomationsEventMapper(private val logger: Logger) {
    fun getEventFromRemoteMessage(messageData: Map<String, String>): AutomationsEvent? {
        try {
            val eventJsonStr = messageData[EVENT] ?: return null

            val eventJsonObj = JSONObject(eventJsonStr)
            val eventName = eventJsonObj.getString(EVENT_NAME)
            if (eventName.isEmpty()) {
                return null
            }

            val jsonDate = eventJsonObj.optLong(EVENT_DATE)
            val eventDate: Date = if (jsonDate == 0L) {
                Date(getCurrentTimeInMillis())
            } else {
                Date(jsonDate.secondsToMilliSeconds())
            }

            val productId = eventJsonObj.optString(EVENT_PRODUCT_ID)
                .takeIf { it.isNotEmpty() }

            val eventType = AutomationsEventType.fromType(eventName)
            return if (eventType != AutomationsEventType.Unknown) {
                AutomationsEvent(eventType, eventDate, productId)
            } else {
                null
            }
        } catch (e: JSONException) {
            logger.release("getEventFromRemoteMessage() -> Failed to retrieve event that triggered push notification")
        }

        return null
    }

    companion object {
        // Payload Data
        private const val EVENT = "qonv.event"
        private const val EVENT_NAME = "name"
        private const val EVENT_DATE = "happened"
        private const val EVENT_PRODUCT_ID = "product_id"
    }
}
