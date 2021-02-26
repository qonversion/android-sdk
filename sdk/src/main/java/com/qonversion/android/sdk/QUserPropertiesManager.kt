package com.qonversion.android.sdk

import android.app.Application
import android.os.Handler
import com.qonversion.android.sdk.SharedPreferencesKeys.CUSTOM_UID_KEY
import com.qonversion.android.sdk.storage.PropertiesStorage
import com.qonversion.android.sdk.storage.SharedPreferencesCache

class QUserPropertiesManager internal constructor(
    private val context: Application,
    private val repository: QonversionRepository,
    private var propertiesStorage: PropertiesStorage,
    private val sharedPreferencesCache: SharedPreferencesCache
) {

    companion object {
        private const val PROPERTY_UPLOAD_PERIOD = 5 * 1000
    }

    init {
        val fbAttributionId = FacebookAttribution().getAttributionId(context.contentResolver)
        if (fbAttributionId != null) {
            propertiesStorage.save(QUserProperties.FacebookAttribution.code, fbAttributionId)
        }

        sendPropertiesAtPeriod()
    }

    private fun sendPropertiesAtPeriod() {
        val mainHandler = Handler(context.mainLooper)
        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                forceSendProperties()
                mainHandler.postDelayed(this, PROPERTY_UPLOAD_PERIOD.toLong())
            }
        }, PROPERTY_UPLOAD_PERIOD.toLong())
    }

    fun setProperty(key: QUserProperties, value: String) {
        propertiesStorage.save(key.code, value)
    }

    fun setUserProperty(key: String, value: String) {
        propertiesStorage.save(key, value)
    }

    fun setUserID(value: String) {
        sharedPreferencesCache.putString(CUSTOM_UID_KEY, value)
        propertiesStorage.save(QUserProperties.CustomUserId.code, value)
    }

    fun forceSendProperties() {
        propertiesStorage.getProperties()
            .takeIf { it.isNotEmpty() }
            ?.let {
                repository.sendProperties(it) {
                    propertiesStorage.clear()
                }
            }
    }
}