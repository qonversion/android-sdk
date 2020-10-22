package com.qonversion.android.sdk

import android.content.ContentResolver
import android.os.Handler

class QUserPropertiesManager internal constructor(
    private val repository: QonversionRepository,
    private val contentResolver: ContentResolver,
    private val mainHandler: Handler
) {

    companion object {
        private const val PROPERTY_UPLOAD_PERIOD = 5 * 1000
    }

    init {
        val fbAttributionId = FacebookAttribution().getAttributionId(contentResolver)
        if (fbAttributionId != null){
            repository.setProperty(
                QUserProperties.FacebookAttribution.userPropertyCode,
                fbAttributionId
            )
        }

        sendPropertiesAtPeriod()
    }

    private fun sendPropertiesAtPeriod() {
        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                repository.sendProperties()
                mainHandler.postDelayed(this, PROPERTY_UPLOAD_PERIOD.toLong())
            }
        }, PROPERTY_UPLOAD_PERIOD.toLong())
    }

    fun setProperty(key: QUserProperties, value: String) {
        repository.setProperty(key.userPropertyCode, value)
    }

    fun setUserProperty(key: String, value: String) {
        repository.setProperty(key, value)
    }

    fun setUserID(value: String) {
        repository.setProperty(QUserProperties.CustomUserId.userPropertyCode, value)
    }

    fun forceSendProperties() {
        repository.sendProperties()
    }
}