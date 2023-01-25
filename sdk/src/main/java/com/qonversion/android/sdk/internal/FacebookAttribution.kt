package com.qonversion.android.sdk.internal

import android.content.ContentResolver
import android.net.Uri

internal class FacebookAttribution {
    fun getAttributionId(contentResolver: ContentResolver): String? {
        val projection =
            arrayOf(ATTRIBUTION_ID_COLUMN_NAME)
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
        if (content == null || !content.moveToFirst()) {
            return null
        }
        val columnIndex = content.getColumnIndex(ATTRIBUTION_ID_COLUMN_NAME)
        if (columnIndex < 0) {
            return null
        }
        val attributionId = content.getString(columnIndex)
        content.close()

        return attributionId
    }

    companion object {
        private val ATTRIBUTION_ID_CONTENT_URI =
            Uri.parse("content://com.facebook.katana.provider.AttributionIdProvider")
        private const val ATTRIBUTION_ID_COLUMN_NAME = "aid"
    }
}
