package io.qonversion.nocodes.interfaces

import io.qonversion.nocodes.dto.QAction
import com.qonversion.android.sdk.Qonversion

interface NoCodesDelegate {

    /**
     * Called when No-Code screen is shown.
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
    fun onActionFailedExecuting(action: QAction) { }

    /**
     * Called when No-Code screen finishes executing an action.
     *
     * @param action executed action.
     * For instance, if the user made a purchase then action.type = [QAction.Type.Purchase].
     * You can use the [Qonversion.checkEntitlements] method to get available permissions.
     */
    fun onActionFinishedExecuting(action: QAction) { }

    /**
     * Called when No-Code flow is finished and the screen is closed.
     */
    fun onFinished() { }
}
