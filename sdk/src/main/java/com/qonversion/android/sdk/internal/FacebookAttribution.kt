package com.qonversion.android.sdk.internal

import android.content.ContentResolver
import android.net.Uri
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

interface FacebookAttributionListener {
  fun onFbAttributionIdResult(id: String?)
}

internal class FacebookAttribution {
    fun getAttributionId(contentResolver: ContentResolver, resultListener: FacebookAttributionListener) {
        val listenerRef = WeakReference(resultListener)
        Executors.newSingleThreadExecutor().execute {
            getAttributionIdAsync(contentResolver, listenerRef)
        }
    }

    private fun getAttributionIdAsync(
        contentResolver: ContentResolver,
        listenerRef: WeakReference<FacebookAttributionListener>
    ) {
        val projection = arrayOf(ATTRIBUTION_ID_COLUMN_NAME)
        val content = try {
            contentResolver.query(
                ATTRIBUTION_ID_CONTENT_URI,
                projection,
                null,
                null,
                null
            )
        } catch (e: Exception) {
            null
        }

        var attributionId: String? = null

        if (content?.moveToFirst() == true) {
            val columnIndex = content.getColumnIndex(ATTRIBUTION_ID_COLUMN_NAME)
            if (columnIndex >= 0) {
                attributionId = content.getString(columnIndex)
                content.close()
            }
        }

        listenerRef.get()?.onFbAttributionIdResult(attributionId)
    }

    companion object {
        private val ATTRIBUTION_ID_CONTENT_URI =
            Uri.parse("content://com.facebook.katana.provider.AttributionIdProvider")
        private const val ATTRIBUTION_ID_COLUMN_NAME = "aid"
    }
}
