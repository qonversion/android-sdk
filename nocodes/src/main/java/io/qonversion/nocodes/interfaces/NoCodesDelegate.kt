package io.qonversion.nocodes.interfaces

import io.qonversion.nocodes.dto.QAction
import io.qonversion.nocodes.dto.QScreenVariable
import io.qonversion.nocodes.NoCodes
import io.qonversion.nocodes.error.NoCodesError
import com.qonversion.android.sdk.Qonversion

interface NoCodesDelegate {

    /**
     * Called when No-Code screen is shown.
     *
     * @param screenId shown screen Id.
     */
    fun onScreenShown(screenId: String) { }

    /**
     * Called when No-Code screen is shown, providing the product ids configured on that screen.
     * Use this to keep analytics consistent with the full set of products the screen was built
     * with, not only the ones the rendered screen requested.
     *
     * The default implementation delegates to [onScreenShown], so existing implementations keep
     * working and this callback fires exactly once.
     *
     * @param screenId shown screen Id.
     * @param products Qonversion product ids configured on the screen (may be empty).
     */
    fun onScreenShown(screenId: String, products: List<String>) {
        onScreenShown(screenId)
    }

    /**
     * Called when No-Code screen is shown, providing the screen variables authored on it.
     * Read them by [QScreenVariable.key] to react to the screen's configured values after it
     * loads; each value keeps its authored type (bool / string / number).
     *
     * The default implementation delegates to [onScreenShown], so existing implementations keep
     * working and this callback fires exactly once.
     *
     * @param screenId shown screen Id.
     * @param products Qonversion product ids configured on the screen (may be empty).
     * @param variables screen variables authored on the screen (may be empty).
     */
    fun onScreenShown(screenId: String, products: List<String>, variables: List<QScreenVariable>) {
        onScreenShown(screenId, products)
    }

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
     * @param value the string value configured for the custom action in the builder.
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
