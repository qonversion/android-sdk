package com.qonversion.android.sdk.automations

import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.automations.internal.AutomationsInternal
import com.qonversion.android.sdk.listeners.QonversionShowScreenCallback

interface Automations {

    companion object {

        private var backingInstance: Automations? = null

        /**
         * Use this variable to get a current initialized instance of the Qonversion SDK.
         * Please, use the variable only after calling [Automations.initialize].
         * Otherwise, trying to access the variable will cause an exception.
         *
         * @return Current initialized instance of the Qonversion SDK.
         * @throws UninitializedPropertyAccessException if the instance has not been initialized
         */
        @JvmStatic
        val sharedInstance: Automations
            get() = backingInstance ?: throw UninitializedPropertyAccessException(
                "Automations have not been initialized. You should call " +
                        "the initialize method before accessing the shared instance of Automations."
            )

        /**
         * An entry point to use Qonversion Automations. Call to initialize Automations.
         * Make sure you have initialized [Qonversion] first.
         *
         * @return Initialized instance of the Qonversion SDK.
         */
        @JvmStatic
        fun initialize(): Automations {
            try {
                Qonversion.sharedInstance
            } catch (e: UninitializedPropertyAccessException) {
                throw UninitializedPropertyAccessException("Qonversion has not been initialized. " +
                        "Automations initialization should be called after Qonversion is initialized.")
            }
            return AutomationsInternal().also {
                backingInstance = it
            }
        }
    }

    /**
     * The delegate is responsible for handling in-app screens and actions when push notification is received.
     * Make sure the method is called before Qonversion.handleNotification
     */
    fun setDelegate(delegate: AutomationsDelegate)

    /**
     * Show the screen using its ID.
     * @param withID - screen's ID that must be shown
     * @param callback - callback that is called when the screen is shown to a user
     */
    fun showScreen(withID: String, callback: QonversionShowScreenCallback)
}
