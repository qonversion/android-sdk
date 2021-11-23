package com.qonversion.android.sdk.old.automations

import com.qonversion.android.sdk.old.billing.getCurrentTimeInMillis
import com.qonversion.android.sdk.old.billing.secondsToMilliSeconds
import com.qonversion.android.sdk.old.logger.Logger
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

            val eventType = AutomationsEventType.fromType(eventName)
            return if (eventType != AutomationsEventType.Unknown) {
                AutomationsEvent(eventType, eventDate)
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
    }
}
