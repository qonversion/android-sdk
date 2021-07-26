package com.qonversion.android.sdk.automations

import com.google.firebase.messaging.RemoteMessage
import com.qonversion.android.sdk.billing.getCurrentTimeInMillis
import com.qonversion.android.sdk.billing.secondsToMilliSeconds
import com.qonversion.android.sdk.logger.Logger
import org.json.JSONException
import org.json.JSONObject
import java.util.*

class AutomationsEventMapper(private val logger: Logger) {
    fun getEventFromRemoteMessage(message: RemoteMessage): AutomationsEvent? {
        try {
            val eventJsonStr = message.data[EVENT]
            if (eventJsonStr != null) {
                val eventJsonObj = JSONObject(eventJsonStr)
                val eventName = eventJsonObj.getString(EVENT_NAME)
                if (eventName.isEmpty()) {
                    return null
                }

                val eventDate: Date = if (!eventJsonObj.isNull(EVENT_DATE)) {
                    val jsonDate = eventJsonObj.getLong(EVENT_DATE)
                    Date(jsonDate.secondsToMilliSeconds())
                } else {
                    Date(getCurrentTimeInMillis())
                }

                val eventType = AutomationsEventType.fromType(eventName)
                return AutomationsEvent(eventType, eventDate)
            }
        } catch (e: JSONException) {
            logger.release("mapAutomationsEvent() -> Failed to retrieve event that triggered push notification")
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