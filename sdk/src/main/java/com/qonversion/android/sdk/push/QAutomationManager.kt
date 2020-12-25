package com.qonversion.android.sdk.push

import android.content.Intent
import android.content.SharedPreferences
import com.google.firebase.messaging.RemoteMessage
import com.qonversion.android.sdk.QonversionActionPointsCallback
import com.qonversion.android.sdk.QonversionError
import com.qonversion.android.sdk.QonversionRepository
import com.qonversion.android.sdk.QonversionScreensCallback
import com.qonversion.android.sdk.dto.QLaunchResult
import com.qonversion.android.sdk.dto.ResponseV2
import com.qonversion.android.sdk.dto.automation.ScreenData
import com.qonversion.android.sdk.push.mvp.ScreenActivity


class QAutomationManager(
    private val automationDelegate: QAutomationDelegate,
    private val repository: QonversionRepository,
    private val preferences: SharedPreferences
) {

    fun handlePushIfPossible(remoteMessage: RemoteMessage): Boolean {
        val pickScreen = remoteMessage.data[PICK_SCREEN]
        if (pickScreen != null && pickScreen == "1") {
            loadScreenIfPossible()
        }
        return true
    }

    fun setPushToken(token: String) {
        val oldToken = loadToken()
        if (!oldToken.equals(token)){
            repository.setPushToken(token)
            saveToken(token)
        }
    }

    fun handleScreens(launchResult: QLaunchResult) {
        val automation = launchResult.userAutomations.firstOrNull()
        if (automation != null && automation.type == AUTOMATION_TYPE_SCREEN) {
            loadScreen(automation.id)
        }
    }

    private fun loadScreenIfPossible() {
        repository.actionPoints(ACTION_POINT_TYPE, ACTION_POINT_STATUS, object : QonversionActionPointsCallback {
                override fun onSuccess(data: List<ResponseV2<ScreenData>>) {
                    val actionPoint = data.lastOrNull()
                    if (actionPoint != null) {
                        loadScreen(actionPoint.data.screenId)
                    }
                }

                override fun onError(error: QonversionError) {

                }
            })
    }

    private fun loadScreen(screenId: String) {
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

    private fun saveToken(token: String) = preferences.edit().putString(PUSH_TOKEN_KEY, token).apply()

    private fun loadToken() = preferences.getString(PUSH_TOKEN_KEY, "")

    companion object {
        private const val PICK_SCREEN = "qonv.pick_screen"
        private const val AUTOMATION_TYPE_SCREEN = "screen"
        private const val ACTION_POINT_TYPE = "screen_view"
        private const val ACTION_POINT_STATUS = 1
        private const val PUSH_TOKEN_KEY = "push_token_key"
    }
}