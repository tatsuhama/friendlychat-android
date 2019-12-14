package com.google.firebase.codelab.friendlychat

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val TAG = "MyFMService"
    private val FRIENDLY_ENGAGE_TOPIC = "friendly_engage"

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Handle data payload of FCM messages.
        Log.d(TAG, "FCM Message Id: " + remoteMessage.messageId)
        Log.d(TAG, "FCM Notification Message: " + remoteMessage.notification)
        Log.d(TAG, "FCM Data Message: " + remoteMessage.data)
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "FCM Token: $token")
        // Once a token is generated, we subscribe to topic.
        FirebaseMessaging.getInstance().subscribeToTopic(FRIENDLY_ENGAGE_TOPIC)
    }
}
