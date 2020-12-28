package com.qonversion.android.sdk.push

import android.content.Intent
import android.content.SharedPreferences
import com.google.firebase.messaging.RemoteMessage
import com.qonversion.android.sdk.QonversionRepository
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
        if (pickScreen.equals(PICK_SCREEN_VALUE)) {
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
            { data ->
                val actionPoint = data.lastOrNull()
                if (actionPoint != null) {
                    logger.debug("loadScreenIfPossible() ->  Screen with id ${actionPoint.data.screenId} was found to show")
                    loadScreen(actionPoint.data.screenId)
                }
            },
            {
                logger.debug("loadScreenIfPossible() -> Failed to retrieve screenId to show")
            }
        )
    }

    private fun getQueryParams(): Map<String, String> {
        val queryParams = HashMap<String, String>()
        return queryParams.apply {
            put(QUERY_PARAM_TYPE_KEY, QUERY_PARAM_TYPE_VALUE)
            put(QUERY_PARAM_ACTIVE_KEY, QUERY_PARAM_ACTIVE_VALUE.toString())
        }
    }

    private fun loadScreen(screenId: String) {
        repository.screens(screenId,
            { htmlPage ->
                val activity = automationDelegate?.provideActivityForScreen()
                if (activity == null) {
                    logger.release("It looks like setAutomationDelegate() was not called")
                    return@screens
                }

                val intent = Intent(activity, ScreenActivity::class.java)
                intent.putExtra(ScreenActivity.INTENT_HTML_PAGE, htmlPage)
                intent.putExtra(ScreenActivity.INTENT_SCREEN_ID, screenId)
                activity.startActivity(intent)
            },
            {
                logger.debug("loadScreen() -> Failed to load screen")
            }
        )
    }

    private fun saveToken(token: String) =
        preferences.edit().putString(PUSH_TOKEN_KEY, token).apply()

    private fun loadToken() = preferences.getString(PUSH_TOKEN_KEY, "")

    companion object {
        private const val PICK_SCREEN = "qonv.pick_screen"
        private const val PICK_SCREEN_VALUE = "1"
        private const val PUSH_TOKEN_KEY = "push_token_key"
        private const val QUERY_PARAM_TYPE_KEY = "type"
        private const val QUERY_PARAM_ACTIVE_KEY = "active"
        private const val QUERY_PARAM_TYPE_VALUE = "screen_view"
        private const val QUERY_PARAM_ACTIVE_VALUE = 1
    }
}