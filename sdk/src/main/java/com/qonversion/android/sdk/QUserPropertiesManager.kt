package com.qonversion.android.sdk

import android.app.Application
import android.os.Handler
import android.os.Looper

class QUserPropertiesManager internal constructor(
    private val context: Application,
    private val repository: QonversionRepository
) {

    companion object {
        private const val PROPERTY_UPLOAD_PERIOD = 5 * 1000
    }

    init {
        val fbAttributionId = FacebookAttribution().getAttributionId(context.contentResolver)

        fbAttributionId?.let {
            repository.setProperty(
                QUserProperties.FacebookAttribution.userPropertyCode,
                it
            )
        }

        sendPropertiesAtPeriod()
    }

    private fun sendPropertiesAtPeriod() {
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(object : Runnable {
            override fun run() {
                repository.sendProperties()
                handler.postDelayed(this, PROPERTY_UPLOAD_PERIOD.toLong())
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