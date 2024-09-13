package com.qonversion.android.sdk.automations

import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.automations.internal.AutomationsInternal
import com.qonversion.android.sdk.listeners.QonversionShowScreenCallback

interface Automations {

    companion object {

        private var backingInstance: Automations? = null

        /**
         * Use this variable to get a current initialized instance of the Qonversion Automations.
         * Please, use Automations only after calling [Qonversion.initialize].
         * Otherwise, trying to access the variable will cause an exception.
         *
         * @return Current initialized instance of the Qonversion Automations.
         * @throws UninitializedPropertyAccessException if Qonversion has not been initialized
         */
        @JvmStatic
        @get:JvmName("getSharedInstance")
        val shared: Automations
            get() {
                if (backingInstance == null) {
                    synchronized(Automations::class.java) {
                        if (backingInstance == null) {
                            try {
                                Qonversion.shared
                            } catch (e: UninitializedPropertyAccessException) {
                                throw UninitializedPropertyAccessException("Qonversion has not been initialized. " +
                                        "Automations should be used after Qonversion is initialized.")
                            }

                            backingInstance = AutomationsInternal()
                        }
                    }
                }

                return backingInstance ?: throw IllegalStateException("Unexpected uninitialized state")
            }
    }

    /**
     * The delegate is responsible for handling in-app screens and actions when push notification is received.
     * Make sure the method is called before [Automations.handleNotification].
     */
    fun setDelegate(delegate: AutomationsDelegate)

    /**
     * The delegate is responsible for customizing screens representation.
     * @param delegate delegate that would be called before opening Qonversion screens.
     */
    fun setScreenCustomizationDelegate(delegate: ScreenCustomizationDelegate)

    /**
     * Show the screen using its ID.
     * @param withID identifier of the screen which must be shown.
     * @param callback callback that is called when the screen is shown to a user.
     */
    fun showScreen(withID: String, callback: QonversionShowScreenCallback)

    /**
     * Set push token to Qonversion to enable Qonversion push notifications
     */
    @Deprecated("Consider removing this method as it isn't needed anymore")
    fun setNotificationsToken(token: String)

    /**
     * @param messageData RemoteMessage payload data
     * @see [RemoteMessage data](https://firebase.google.com/docs/reference/android/com/google/firebase/messaging/RemoteMessage#public-mapstring,-string-getdata)
     * @return true when a push notification was received from Qonversion.
     *         Otherwise returns false, so you need to handle a notification yourself.
     */
    @Deprecated("Consider removing this method. Qonversion is not working with push notifications anymore")
    fun handleNotification(messageData: Map<String, String>): Boolean

    /**
     * Get parsed custom payload, which you added to the notification in the dashboard
     * @param messageData RemoteMessage payload data
     * @return a map with custom payload from the notification or null if it's not provided.
     */
    fun getNotificationCustomPayload(messageData: Map<String, String>): Map<String, Any?>?
}
