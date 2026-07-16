package io.qonversion.nocodes.interfaces

import io.qonversion.nocodes.dto.QAction
import io.qonversion.nocodes.NoCodes
import io.qonversion.nocodes.error.NoCodesError
import com.qonversion.android.sdk.Qonversion

interface NoCodesDelegate {

    /**
     * Called when No-Code screen is shown.
     *
     * To read the screen's configured default variables, load the screen entity via
     * [NoCodes.loadScreen] and use [io.qonversion.nocodes.dto.QNoCodeScreen.defaultVariables].
     *
     * @param screenId shown screen Id.
     */
    fun onScreenShown(screenId: String) { }

    /**
     * Called when No-Code screen starts executing an action.
     *
     * @param action action that is being executed.
     */
    fun onActionStartedExecuting(action: QAction) { }

    /**
     * Called when No-Code screen fails executing an action.
     *
     * @param action failed action.
     */
    fun onActionFailedToExecute(action: QAction) { }

    /**
     * Called when No-Code screen finishes executing an action.
     *
     * @param action executed action.
     * For instance, if the user made a purchase then action.type = [QAction.Type.Purchase].
     * You can use the [Qonversion.checkEntitlements] method to get available permissions.
     */
    fun onActionFinishedExecuting(action: QAction) { }

    /**
     * Called when a custom action configured in the builder is triggered on the screen.
     * The No-Codes SDK does not execute anything itself — handle the value in your app code.
     * The screen stays open; close it using [NoCodes.close] if needed.
     *
     * @param value the string value configured for the custom action in the builder,
     * or an empty string if no value was configured.
     */
    fun onCustomAction(value: String) { }

    /**
     * Called when No-Code flow is finished and the screen is closed.
     */
    fun onFinished() { }

    /**
     * Called when No-Code screen fails to load.
     * Don't forget to close the screen using [NoCodes.close] method.
     * @param error The error that occurred while loading the screen.
     */
    fun onScreenFailedToLoad(error: NoCodesError) { }
}
