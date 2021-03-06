package com.qonversion.android.sdk.automations

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.SharedPreferences
import com.google.firebase.messaging.RemoteMessage
import com.qonversion.android.sdk.QonversionRepository
import com.qonversion.android.sdk.billing.toBoolean
import com.qonversion.android.sdk.logger.ConsoleLogger
import com.qonversion.android.sdk.automations.mvp.ScreenActivity
import java.lang.Exception
import java.lang.ref.WeakReference
import javax.inject.Inject

class QAutomationsManager @Inject constructor(
    private val repository: QonversionRepository,
    private val preferences: SharedPreferences,
    private val appContext: Application
) {
    private val logger = ConsoleLogger()

    @Volatile
    var automationsDelegate: WeakReference<AutomationsDelegate>? = null
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

    fun automationsDidStartExecuting(actionResult: QActionResult) {
        automationsDelegate?.get()?.automationsDidStartExecuting(actionResult)
            ?: logDelegateErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    fun automationsDidFailExecuting(actionResult: QActionResult) {
        automationsDelegate?.get()?.automationsDidFailExecuting(actionResult)
            ?: logDelegateErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    fun automationsDidFinishExecuting(actionResult: QActionResult) {
        automationsDelegate?.get()?.automationsDidFinishExecuting(actionResult)
            ?: logDelegateErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    fun automationsDidShowScreen(screenId: String) {
        automationsDelegate?.get()?.automationsDidShowScreen(screenId)
            ?: logDelegateErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    fun automationsFinished() {
        automationsDelegate?.get()?.automationsFinished()
            ?: logDelegateErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    private fun logDelegateErrorForFunctionName(functionName: String?) {
        logger.release("AutomationsDelegate.$functionName() function can not be executed. It looks like Automations.setDelegate() was not called or delegate has been destroyed by GC")
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
                val context = automationsDelegate?.get()?.contextForScreenIntent() ?: appContext

                val intent = Intent(context, ScreenActivity::class.java)
                intent.putExtra(ScreenActivity.INTENT_HTML_PAGE, screen.htmlPage)
                intent.putExtra(ScreenActivity.INTENT_SCREEN_ID, screenId)
                if (context !is Activity) {
                    intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
                    logger.release("loadScreen() -> Screen intent will process with a non-Activity context")
                }

                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    logger.release("loadScreen() -> Failed to start screen with id $screenId with exception: $e")
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