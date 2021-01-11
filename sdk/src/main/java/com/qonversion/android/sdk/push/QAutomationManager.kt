package com.qonversion.android.sdk.push

import android.content.Intent
import android.content.SharedPreferences
import com.google.firebase.messaging.RemoteMessage
import com.qonversion.android.sdk.QonversionRepository
import com.qonversion.android.sdk.billing.toBoolean
import com.qonversion.android.sdk.logger.ConsoleLogger
import com.qonversion.android.sdk.push.mvp.ScreenActivity

class QAutomationManager(
    private val repository: QonversionRepository,
    private val preferences: SharedPreferences
) {
    private val logger = ConsoleLogger()

    @Volatile
    var automationDelegate: QAutomationDelegate? = null
        @Synchronized set
        @Synchronized get

    fun handlePushIfPossible(remoteMessage: RemoteMessage): Boolean {
        val pickScreen = remoteMessage.data[PICK_SCREEN]
        if (pickScreen.toBoolean()) {
            logger.release("handlePushIfPossible() -> Qonversion push notification was received")
            loadScreenIfPossible()
            return true
        }
        return false
    }

    fun setPushToken(token: String) {
        val oldToken = loadToken()
        if (!oldToken.equals(token)) {
            repository.setPushToken(token)
            saveToken(token)
        }
    }

    fun automationFlowFinishedWithAction(action: QAction) {
        automationDelegate?.automationFlowFinishedWithAction(action)
    }

    private fun loadScreenIfPossible() {
        repository.actionPoints(
            getQueryParams(),
            { actionPoint ->
                if (actionPoint != null) {
                    logger.debug("loadScreenIfPossible() ->  Screen with id ${actionPoint.screenId} was found to show")
                    loadScreen(actionPoint.screenId)
                }
            },
            {
                logger.debug("loadScreenIfPossible() -> Failed to retrieve screenId to show")
            }
        )
    }

    private fun getQueryParams(): Map<String, String> {
        return mapOf(
            QUERY_PARAM_TYPE to QUERY_PARAM_TYPE_VALUE,
            QUERY_PARAM_ACTIVE to QUERY_PARAM_ACTIVE_VALUE.toString()
        )
    }

    private fun loadScreen(screenId: String) {
        repository.screens(screenId,
            { screen ->
                val activity = automationDelegate?.provideActivityForScreen()
                if (activity == null) {
                    logger.release("It looks like setAutomationDelegate() was not called")
                    return@screens
                }

                val intent = Intent(activity, ScreenActivity::class.java)
                intent.putExtra(ScreenActivity.INTENT_HTML_PAGE, screen.htmlPage)
                intent.putExtra(ScreenActivity.INTENT_SCREEN_ID, screenId)
                activity.startActivity(intent)
            },
            {
                logger.release("loadScreen() -> Failed to load screen with id $screenId")
            }
        )
    }

    private fun saveToken(token: String) =
        preferences.edit().putString(PUSH_TOKEN_KEY, token).apply()

    private fun loadToken() = preferences.getString(PUSH_TOKEN_KEY, "")

    companion object {
        private const val PICK_SCREEN = "qonv.pick_screen"
        private const val PUSH_TOKEN_KEY = "push_token_key"
        private const val QUERY_PARAM_TYPE = "type"
        private const val QUERY_PARAM_ACTIVE = "active"
        private const val QUERY_PARAM_TYPE_VALUE = "screen_view"
        private const val QUERY_PARAM_ACTIVE_VALUE = 1
    }
}