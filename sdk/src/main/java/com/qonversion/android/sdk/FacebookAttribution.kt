package com.qonversion.android.sdk

import android.content.ContentResolver
import android.net.Uri

class FacebookAttribution {
    fun getAttributionId(contentResolver: ContentResolver): String? {
        val projection =
            arrayOf(ATTRIBUTION_ID_COLUMN_NAME)
        val c = contentResolver.query(
            ATTRIBUTION_ID_CONTENT_URI,
            projection,
            null,
            null,
            null
        )
        if (c == null || !c.moveToFirst()) {
            return null
        }
        val attributionId =
            c.getString(c.getColumnIndex(ATTRIBUTION_ID_COLUMN_NAME))
        c.close()

        return attributionId
    }

    companion object {
        private  val ATTRIBUTION_ID_CONTENT_URI =
            Uri.parse("content://com.facebook.katana.provider.AttributionIdProvider")
        private const val ATTRIBUTION_ID_COLUMN_NAME = "aid"
    }
}