package com.qonversion.android.sdk.screens

import android.content.Intent
import com.qonversion.android.sdk.QonversionError
import com.qonversion.android.sdk.QonversionRepository
import com.qonversion.android.sdk.QonversionScreensCallback
import com.qonversion.android.sdk.screens.mvp.ScreenActivity


class QScreenManager(
    private val automationDelegate: QAutomationDelegate,
    private val repository: QonversionRepository
) {
    fun handlePushIfPossible(url: String): Boolean {
        val screenId = fetchScreenId()
        loadScreen(screenId)
        return true
    }

    fun setPushToken(token: String) {
        repository.setPushToken(token)
    }

    fun loadScreen(screenId: String) {
        repository.screens(screenId, object : QonversionScreensCallback {
            override fun onSuccess(htmlPage: String) {
                val activity = automationDelegate.provideActivityForScreen()

                val intent = Intent(activity, ScreenActivity::class.java)
                intent.putExtra(ScreenActivity.INTENT_HTML_PAGE, htmlPage)
                intent.putExtra(ScreenActivity.INTENT_SCREEN_ID, screenId)
                activity.startActivity(intent)
            }

            override fun onError(error: QonversionError) {

            }
        })
    }

    private fun fetchScreenId(): String {
        return "screen-uid-789-1"

    }
}