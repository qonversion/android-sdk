package com.qonversion.android.sdk.push

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.SharedPreferences
import com.google.firebase.messaging.RemoteMessage
import com.qonversion.android.sdk.QonversionRepository
import com.qonversion.android.sdk.billing.toBoolean
import com.qonversion.android.sdk.logger.ConsoleLogger
import com.qonversion.android.sdk.push.mvp.ScreenActivity
import java.lang.Exception
import java.lang.ref.WeakReference

class QAutomationsManager(
    private val repository: QonversionRepository,
    private val preferences: SharedPreferences
) {
    private val logger = ConsoleLogger()

    @Volatile
    var automationsDelegate: WeakReference<QAutomationsDelegate>? = null
        @Synchronized set
        @Synchronized get

    fun handlePushIfPossible(remoteMessage: RemoteMessage): Boolean {
        val pickScreen = remoteMessage.data[PICK_SCREEN]

        return pickScreen.toBoolean().also {
            if (it) {
                logger.release("handlePushIfPossible() -> Qonversion push notification was received")
                loadScreenIfPossible()
            }
        }
    }

    fun setPushToken(token: String) {
        val oldToken = loadToken()
        if (!oldToken.equals(token)) {
            repository.setPushToken(token)
            saveToken(token)
        }
    }

    fun automationFinishedWithAction(actionResult: QActionResult) {
        val weakReference = automationsDelegate?.get() ?: run {
            logger.release("automationFlowFinishedWithAction() -> It looks like Automations.setDelegate() was not called")
            return
        }

        weakReference.automationFinishedWithAction(actionResult)
    }

    private fun loadScreenIfPossible() {
        repository.actionPoints(
            getQueryParams(),
            { actionPoint ->
                actionPoint?.let {
                    logger.debug("loadScreenIfPossible() ->  Screen with id ${it.screenId} was found to show")
                    loadScreen(it.screenId)
                } ?: logger.release("loadScreenIfPossible() ->  No screens to show")
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
                val context = automationsDelegate?.get()?.contextForScreenIntent()
                if (context == null) {
                    logger.release("loadScreen() -> It looks like Automations.setDelegate() was not called")
                    return@screens
                }

                val intent = Intent(context, ScreenActivity::class.java)
                intent.putExtra(ScreenActivity.INTENT_HTML_PAGE, screen.htmlPage)
                intent.putExtra(ScreenActivity.INTENT_SCREEN_ID, screenId)
                intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    logger.release("loadScreen() -> Failed to start screen with id $screenId")
                }
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