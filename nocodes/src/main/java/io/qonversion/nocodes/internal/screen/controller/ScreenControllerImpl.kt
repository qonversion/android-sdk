package io.qonversion.nocodes.internal.screen.controller

import android.app.Activity
import android.content.Context
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import io.qonversion.nocodes.dto.QScreenPresentationConfig
import io.qonversion.nocodes.error.ErrorCode
import io.qonversion.nocodes.error.NoCodesException
import io.qonversion.nocodes.internal.common.BaseClass
import io.qonversion.nocodes.internal.dto.config.InternalConfig
import io.qonversion.nocodes.internal.logger.Logger
import io.qonversion.nocodes.internal.screen.getScreenTransactionAnimations
import io.qonversion.nocodes.internal.screen.misc.ActivityProvider
import io.qonversion.nocodes.internal.screen.service.ScreenService
import io.qonversion.nocodes.internal.screen.view.ScreenActivity
import java.lang.Exception

internal class ScreenControllerImpl(
    private val screenService: ScreenService,
    private val internalConfig: InternalConfig,
    private val activityProvider: ActivityProvider,
    private val appContext: Context,
    logger: Logger,
) : ScreenController, BaseClass(logger) {

    override suspend fun showScreen(screenId: String) {
        logger.verbose("showScreen() -> Fetching the screen with id $screenId from the API")
        val screen = screenService.getScreen(screenId)

        val context: Context = activityProvider.getCurrentActivity() ?: appContext

        val screenPresentationConfig = internalConfig.screenCustomizationDelegate?.get()
            ?.getPresentationConfigurationForScreen(screenId) ?: QScreenPresentationConfig()
        val intent = ScreenActivity.getCallingIntent(
            context,
            screenId,
            screen.body,
            screenPresentationConfig.presentationStyle
        )
        if (context !is Activity) {
            intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
            logger.info("showScreen() -> Screen intent will process with a non-activity context")
        }

        try {
            logger.verbose("showScreen() -> Launching the screen activity")
            context.startActivity(intent)
            getScreenTransactionAnimations(screenPresentationConfig.presentationStyle)?.let { transitionAnimations ->
                if (context is Activity) {
                    val (openAnimation, closeAnimation) = transitionAnimations
                    context.overridePendingTransition(openAnimation, closeAnimation)
                } else {
                    logger.warn("Can't use transition animations, cause the provided context is not an activity.")
                }
            }
        } catch (e: Exception) {
            val errorMessage = "Failed to start screen with id $screenId with exception: $e"
            logger.error("showScreen() -> $errorMessage")
            throw NoCodesException(ErrorCode.ActivityStart, "Failed to start screen with id $screenId", e)
        }
    }
}