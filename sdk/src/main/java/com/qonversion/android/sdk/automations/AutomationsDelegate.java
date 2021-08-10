package com.qonversion.android.sdk.automations;

import android.content.Context;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

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

    /**
     * Called when Automation event is being processed.
     * For example, you have set up push notifications for various events, such as purchase, cancellation of trial, etc.
     * If Qonversion sent a push notification with an event, and you want to handle the event yourself (for example, show your custom screen),
     * then override this function and return false.
     * Otherwise, Qonversion will handle this event itself and show the Automation screen (if it's configured).
     *
     * @param event event that triggered the Automation
     * @param payload notification payload
     * @return false if you want to handle Automation yourself
     * @see [Automation Overview](https://documentation.qonversion.io/docs/automations)
     */
    default Boolean shouldHandleEvent(@NotNull AutomationsEvent event, @NotNull Map<String, String> payload) {
        return true;
    }
}
