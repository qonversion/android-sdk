package com.qonversion.android.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.qonversion.android.sdk.Qonversion


class FirebaseMessageReceiver : FirebaseMessagingService() {
    private val tag = "FirebaseMessageReceiver"

    /**
     *  The token used for sending messages to the application.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(tag, "onNewToken: $token")
        Qonversion.setNotificationsToken(token)
    }

    /**
     * Called when a message is received.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(tag, "onMessageReceived: ")
        // Qonversion sends data messages payload with *title* and *body* custom keys
        if (remoteMessage.data.isNotEmpty()) {
            showNotification(
                remoteMessage.data["title"],
                remoteMessage.data["body"],
                remoteMessage
            )
        }

        // Since here we do not have any notification payload,
        // This section is commented out.
        // In case you expect notification payload, uncomment the block.
        /*if (remoteMessage.notification != null) {
            // Since the notification is received directly from
            // FCM, the title and the body can be fetched directly as below.
            showNotification(
                remoteMessage.notification?.title,
                remoteMessage.notification?.body
            )
        }*/
    }

    private fun showNotification(
        title: String?,
        body: String?,
        remoteMessage: RemoteMessage? = null
    ) {
        val intent = Intent(this, MainActivity::class.java)

        // If you need, set the intent flag for activity
        // FLAG_ACTIVITY_CLEAR_TOP clears the activities present in the activity stack,
        // on the top of the Activity that is to be launched
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.putExtra(INTENT_REMOTE_MESSAGE, remoteMessage)

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT
        )

        val builder = NotificationCompat.Builder(
            applicationContext,
            CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true) // Flag true will make the notification automatically canceled when the user clicks it in the panel.
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setContentTitle(title)
            .setContentText(body)
            .setColor(ContextCompat.getColor(applicationContext, R.color.colorQonversionBlue))

        // Create an object of NotificationManager class to notify the user
        val notificationManager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Check if the Android Version is greater than Oreo
        if (Build.VERSION.SDK_INT
            >= Build.VERSION_CODES.O
        ) {
            val notificationChannel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(
                notificationChannel
            )
        }

        // If a notification with the same id has already been posted by your application and has not yet been canceled,
        // it will be replaced by the updated information.
        notificationManager.notify(0, builder.build())
    }

    companion object {
        const val INTENT_REMOTE_MESSAGE = "remoteMessage"
        private const val CHANNEL_ID = "qonv_id"
        private const val CHANNEL_NAME = "Qonversion"
    }
}