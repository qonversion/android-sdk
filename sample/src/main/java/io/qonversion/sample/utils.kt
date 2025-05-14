package io.qonversion.sample

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import com.qonversion.android.sdk.dto.QonversionError

private const val QONVERSION_PREFS = "qonversion_config"
private const val KEY_PROJECT_KEY = "project_key"
private const val KEY_API_URL = "api_url"
private fun getQonversionPrefs(context: Context): SharedPreferences = context.getSharedPreferences(QONVERSION_PREFS, 0)

fun getProjectKey(context: Context, defaultKey: String): String =
    getQonversionPrefs(context).getString(KEY_PROJECT_KEY, defaultKey) ?: defaultKey

fun getApiUrl(context: Context): String? =
    getQonversionPrefs(context).getString(KEY_API_URL, null)

fun storeQonversionPrefs(context: Context, projectKey: String, apiUrl: String?) {
    val prefs = getQonversionPrefs(context)
    prefs.edit().apply {
        if (projectKey.isBlank()) {
            remove(KEY_PROJECT_KEY)
        } else {
            putString(KEY_PROJECT_KEY, projectKey)
        }

        if (apiUrl.isNullOrBlank()) {
            remove(KEY_API_URL)
        } else {
            putString(KEY_API_URL, apiUrl)
        }

        commit()
    }
}

fun showError(context: Context, error: QonversionError, logTag: String) {
    Toast.makeText(context, error.description, Toast.LENGTH_LONG).show()
    val msg = "error code: ${error.code}, description: ${error.description}, additionalMessage: ${error.additionalMessage}"
    Log.e(logTag, msg)
}
