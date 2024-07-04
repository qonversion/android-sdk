package com.qonversion.android.sdk.automations.internal

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.SharedPreferences
import com.qonversion.android.sdk.automations.AutomationsDelegate
import com.qonversion.android.sdk.automations.ScreenCustomizationDelegate
import com.qonversion.android.sdk.automations.dto.QActionResult
import com.qonversion.android.sdk.automations.dto.QScreenPresentationConfig
import com.qonversion.android.sdk.internal.Constants.PENDING_PUSH_TOKEN_KEY
import com.qonversion.android.sdk.internal.Constants.PUSH_TOKEN_KEY
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.QonversionErrorCode
import com.qonversion.android.sdk.internal.repository.QRepository
import com.qonversion.android.sdk.listeners.QonversionShowScreenCallback
import com.qonversion.android.sdk.internal.toBoolean
import com.qonversion.android.sdk.internal.logger.ConsoleLogger
import com.qonversion.android.sdk.automations.mvp.ScreenActivity
import com.qonversion.android.sdk.internal.provider.AppStateProvider
import com.qonversion.android.sdk.internal.toMap
import java.lang.Exception
import java.lang.ref.WeakReference
import javax.inject.Inject
import org.json.JSONException
import org.json.JSONObject

internal class QAutomationsManager @Inject constructor(
    private val repository: QRepository,
    private val preferences: SharedPreferences,
    private val eventMapper: AutomationsEventMapper,
    private val appContext: Application,
    private val activityProvider: ActivityProvider,
    private val appStateProvider: AppStateProvider
) {
    @Volatile
    var automationsDelegate: WeakReference<AutomationsDelegate>? = null
        @Synchronized set
        @Synchronized get

    @Volatile
    var screenCustomizationDelegate: WeakReference<ScreenCustomizationDelegate>? = null
        @Synchronized set
        @Synchronized get

    private val logger = ConsoleLogger()
    private var pendingToken: String? = null
    internal var isLaunchFinished = false

    fun onAppForeground() {
        pendingToken?.let {
            sendPushToken(it)
        }
    }

    fun handlePushIfPossible(messageData: Map<String, String>): Boolean {
        val pickScreen = messageData[PICK_SCREEN]

        return pickScreen.toBoolean().also {
            if (it) {
                logger.release("handlePushIfPossible() -> Qonversion push notification was received")

                var shouldShowScreen = true

                val event = eventMapper.getEventFromRemoteMessage(messageData)
                if (event != null) {
                    shouldShowScreen =
                        automationsDelegate?.get()?.shouldHandleEvent(event, messageData) ?: true
                }

                if (shouldShowScreen) {
                    loadScreenIfPossible()
                }
            }
        }
    }

    fun getNotificationCustomPayload(messageData: Map<String, String>): Map<String, Any?>? {
        return messageData[KEY_CUSTOM_PAYLOAD]?.let {
            try {
                JSONObject(it).toMap()
            } catch (e: JSONException) {
                null
            }
        }
    }

    fun setPushToken(token: String) {
        val oldToken = loadToken()
        if (token.isEmpty() || oldToken.equals(token)) {
            return
        }

        savePendingTokenToPref(token)
        if (!isLaunchFinished || appStateProvider.appState.isBackground()) {
            pendingToken = token
        } else {
            sendPushToken(token)
        }
    }

    private fun processPushToken() {
        val token = getPendingToken()
        if (!token.isNullOrEmpty()) {
            sendPushToken(token)
        }
    }

    internal fun onLaunchProcessed() {
        isLaunchFinished = true
        processPushToken()
    }

    fun loadScreen(screenId: String, callback: QonversionShowScreenCallback? = null) {
        repository.screens(screenId,
            { screen ->
                val context: Context = activityProvider.getCurrentActivity() ?: appContext

                val screenPresentationConfig = screenCustomizationDelegate?.get()
                    ?.getPresentationConfigurationForScreen(screenId) ?: QScreenPresentationConfig()
                val intent = ScreenActivity.getCallingIntent(
                    context,
                    screenId,
                    screen.htmlPage,
                    screenPresentationConfig.presentationStyle
                )
                if (context !is Activity) {
                    intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
                    logger.debug("loadScreen() -> Screen intent will process with a non-Activity context")
                }

                try {
                    context.startActivity(intent)
                    getScreenTransactionAnimations(screenPresentationConfig.presentationStyle)?.let { transitionAnimations ->
                        if (context is Activity) {
                            val (openAnimation, closeAnimation) = transitionAnimations
                            context.overridePendingTransition(openAnimation, closeAnimation)
                        } else {
                            logger.debug(
                                "Can't use transition animations, cause the provided context is not an activity. " +
                                        "To override default animation, please, provide an activity context to AutomationsDelegate.contextForScreenIntent"
                            )
                        }
                    }
                    callback?.onSuccess()
                } catch (e: Exception) {
                    val errorMessage = "Failed to start screen with id $screenId with exception: $e"
                    logger.error("loadScreen() -> $errorMessage")
                    callback?.onError(
                        QonversionError(
                            QonversionErrorCode.Unknown,
                            errorMessage
                        )
                    )
                }
            },
            {
                val errorMessage =
                    "Failed to load screen with id $screenId. ${it.additionalMessage}"
                logger.error("loadScreen() -> $errorMessage")
                callback?.onError(QonversionError(it.code, errorMessage))
            }
        )
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
        logger.error(
            "AutomationsDelegate.$functionName() function can not be executed. " +
                    "It looks like Automations.setDelegate() was not called or delegate has been destroyed by GC"
        )
    }

    private fun loadScreenIfPossible() {
        repository.actionPoints(
            getQueryParams(),
            { actionPoint ->
                actionPoint?.let {
                    logger.debug("loadScreenIfPossible() ->  Screen with id ${it.screenId} was found to show")
                    loadScreen(it.screenId)
                } ?: logger.warn("loadScreenIfPossible() ->  No screens to show")
            },
            {
                logger.error("loadScreenIfPossible() -> Failed to retrieve screenId to show")
            }
        )
    }

    private fun getQueryParams(): Map<String, String> {
        return mapOf(
            QUERY_PARAM_TYPE to QUERY_PARAM_TYPE_VALUE,
            QUERY_PARAM_ACTIVE to QUERY_PARAM_ACTIVE_VALUE.toString()
        )
    }

    private fun sendPushToken(token: String) {
        repository.sendPushToken(token)

        pendingToken = null
    }

    private fun getPendingToken(): String? {
        return preferences.getString(PENDING_PUSH_TOKEN_KEY, null)
    }

    private fun savePendingTokenToPref(token: String) =
        preferences.edit().putString(PENDING_PUSH_TOKEN_KEY, token).apply()

    private fun loadToken() = preferences.getString(PUSH_TOKEN_KEY, "")

    companion object {
        private const val PICK_SCREEN = "qonv.pick_screen"
        private const val KEY_CUSTOM_PAYLOAD = "qonv.custom_payload"
        private const val QUERY_PARAM_TYPE = "type"
        private const val QUERY_PARAM_ACTIVE = "active"
        private const val QUERY_PARAM_TYPE_VALUE = "screen_view"
        private const val QUERY_PARAM_ACTIVE_VALUE = 1
    }
}
