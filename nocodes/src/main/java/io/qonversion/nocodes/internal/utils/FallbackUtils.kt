package io.qonversion.nocodes.internal.utils

import android.content.Context

object FallbackUtils {

    /**
     * Checks if fallback file is actually available in assets
     */
    fun isFallbackFileAvailable(fileName: String, context: Context): Boolean {
        return try {
            context.assets.open(fileName).use { true }
        } catch (e: Exception) {
            false
        }
    }
}
