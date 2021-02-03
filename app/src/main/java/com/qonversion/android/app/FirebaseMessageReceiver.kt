package com.qonversion.android.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.qonversion.android.sdk.Qonversion


class FirebaseMessageReceiver : FirebaseMessagingService() {
    private val tag = "FirebaseMessageReceiver"

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
                remoteMessage.notification?.body,
                remoteMessage
            )
        }*/
    }

    private fun showNotification(
        title: String?,
        body: String?,
        remoteMessage: RemoteMessage
    ) {
        val notification = createNotification(title, body, remoteMessage)

        createNotificationChannel()

        // Show the notification with notificationId.
        // It is a unique int for each notification that you must define
        val notificationId = 0
        with(NotificationManagerCompat.from(this)) {
            notify(notificationId, notification.build())
        }
    }

    private fun createNotification(
        title: String?,
        body: String?,
        remoteMessage: RemoteMessage
    ): NotificationCompat.Builder {
        val pendingIntent = createPendingIntent(remoteMessage)

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true) // Flag true will make the notification automatically canceled when the user clicks it in the panel.
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setContentTitle(title)
            .setContentText(body)
            .setColor(ContextCompat.getColor(this, R.color.colorQonversionBlue))
    }

    private fun createPendingIntent(remoteMessage: RemoteMessage): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        // If you need, set the intent flag for activity
        // FLAG_ACTIVITY_CLEAR_TOP clears the activities present in the activity stack,
        // on the top of the Activity that is to be launched
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.putExtra(INTENT_REMOTE_MESSAGE, remoteMessage)

        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT
        )
    }

    private fun createNotificationChannel() {
        // If the Android Version is greater than Oreo,
        // then create the NotificationChannel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val INTENT_REMOTE_MESSAGE = "remoteMessage"
        private const val CHANNEL_ID = "qonversion"
    }
}