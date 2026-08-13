package io.qonversion.sample

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import com.qonversion.android.sdk.dto.QonversionError

/**
 * Remote Config v2 local playground.
 *
 * These point the sample at the docker dev environment so the RC v2 screen can complete a real
 * customer journey: publish a config in the local dashboard, fetch it here.
 *
 * - `10.0.2.2` is how the Android emulator reaches the host machine's loopback; `7101` is the local
 *   api-gateway, which owns the SDK-facing `v3/remote-config-v2` routes. Plain http is fine
 *   because the manifest permits cleartext and `network_security_config.xml` already trusts this
 *   host. On a physical device replace it with the host's LAN address.
 * - The project key must belong to the one project on the gateway's RC v2 allowlist; any other
 *   project answers 404 by design.
 * - The environment uid is checked against every snapshot envelope the SDK receives, so it must
 *   match the environment the release was published into exactly.
 */
const val RC_V2_PLAYGROUND_PROJECT_KEY = "ZKyxaGP3A0AGiUgZzyhbuolC-U0FQrlx"
const val RC_V2_PLAYGROUND_BASE_URL = "http://10.0.2.2:7101"
const val RC_V2_PLAYGROUND_ENVIRONMENT_UID = "d3v00000-0000-4000-8000-000000000001"

private const val QONVERSION_PREFS = "qonversion_config"
private const val KEY_PROJECT_KEY = "project_key"
private const val KEY_API_URL = "api_url"
private const val KEY_CONTEXT_KEY = "context_key"

private fun getQonversionPrefs(context: Context): SharedPreferences = context.getSharedPreferences(QONVERSION_PREFS, 0)

fun getProjectKey(context: Context, defaultKey: String): String =
    getQonversionPrefs(context).getString(KEY_PROJECT_KEY, defaultKey) ?: defaultKey

fun getApiUrl(context: Context): String? =
    getQonversionPrefs(context).getString(KEY_API_URL, null)

fun getLastContextKey(context: Context): String =
    getQonversionPrefs(context).getString(KEY_CONTEXT_KEY, "") ?: ""

fun saveLastContextKey(context: Context, contextKey: String) =
    getQonversionPrefs(context).edit().putString(KEY_CONTEXT_KEY, contextKey).apply()

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
