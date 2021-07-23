package com.qonversion.android.sdk.automations

import com.google.firebase.messaging.RemoteMessage
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
                val eventDate = eventJsonObj.getLong(EVENT_DATE)
                val date = Date(eventDate.secondsToMilliSeconds())
                val eventType = AutomationsEventType.fromType(eventName)

                return AutomationsEvent(eventType, date)
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