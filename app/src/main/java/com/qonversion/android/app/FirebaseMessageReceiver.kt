package com.qonversion.android.app

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.qonversion.android.app.App.CHANNEL_ID
import com.qonversion.android.sdk.automations.Automations

class FirebaseMessageReceiver : FirebaseMessagingService() {
    private val tag = "FirebaseMessageReceiver"

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(tag, "onNewToken: $token")
        Automations.shared.setNotificationsToken(token)
    }

    /**
     * Called when a message is received.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(tag, "onMessageReceived: ")

        val title = remoteMessage.data["title"]
        val body = remoteMessage.data["body"]

        // 1. Create an explicit intent for an Activity in your app
        val intent = Intent(this, MainActivity::class.java)
        // If you need, set the intent flag for activity
        // FLAG_ACTIVITY_CLEAR_TOP clears the activities present in the activity stack,
        // on the top of the Activity that is to be launched
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        // Save the RemoteMessage object as extra data
        intent.putExtra(INTENT_REMOTE_MESSAGE, remoteMessage)
        // Flag FLAG_ONE_SHOT indicates that this PendingIntent can be used only once
        val pendingIntent: PendingIntent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            } else {
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_ONE_SHOT)
            }

        // 2. Create a notification
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentTitle(title)
            .setContentText(body)
            .setColor(ContextCompat.getColor(this, R.color.colorQonversionBlue))
            // Set the intent that will fire when the user taps the notification
            .setContentIntent(pendingIntent)

        // 3. Show the notification
        with(NotificationManagerCompat.from(this)) {
            // For notificationId use any unique value you want to be an ID for the notification. 
            notify(0, notificationBuilder.build())
        }
    }

    companion object {
        const val INTENT_REMOTE_MESSAGE = "remoteMessage"
    }
}