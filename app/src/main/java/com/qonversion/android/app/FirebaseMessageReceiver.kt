package com.qonversion.android.app

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.qonversion.android.sdk.Qonversion


class FirebaseMessageReceiver : FirebaseMessagingService() {
    private val TAG = "FirebaseMessageReceiver"

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "onNewToken: $token")
        Qonversion.setPushToken(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "onMessageReceived: ")
        if (!Qonversion.handlePushIfPossible(remoteMessage)) {
            //handle notification yourself
        }
    }
}