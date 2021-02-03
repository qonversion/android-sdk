package com.qonversion.android.sdk.automations;

import android.content.Context;

import org.jetbrains.annotations.NotNull;

public interface AutomationsDelegate {

    /**
     * Returns the context for screen intent
     */
    default Context contextForScreenIntent() {
        return null;
    }

    /**
     * Called when Automations' screen is shown
     *
     * @param screenId shown screen Id
     */
    default void automationsDidShowScreen(@NotNull String screenId) {
    }

    /**
     * Called when Automations flow starts executing an action
     *
     * @param actionResult action that is being executed
     */
    default void automationsDidStartExecuting(@NotNull QActionResult actionResult) {
    }

    /**
     * Called when Automations flow fails executing an action
     *
     * @param actionResult failed action
     */
    default void automationsDidFailExecuting(@NotNull QActionResult actionResult) {
    }

    /**
     * Called when Automations flow finishes executing an action
     *
     * @param actionResult executed action.
     *                     For instance, if the user made a purchase then action.type = QActionResultType.Purchase.
     *                     You can use the Qonversion.checkPermissions() method to get available permissions
     */
    default void automationsDidFinishExecuting(@NotNull QActionResult actionResult) {
    }

    /**
     * Called when Automations flow is finished and the Automations screen is closed
     */
    default void automationsFinished() {
    }
}
