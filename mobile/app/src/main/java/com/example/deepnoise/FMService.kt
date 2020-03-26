package com.example.deepnoise

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FMService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: " + remoteMessage.from)
        // Check if message contains a data payload.
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(
                TAG,
                "Message data payload: " + remoteMessage.data
            )
            updateState(remoteMessage.data)
            // TODO: establish WebSocket connection with server
            // to see what it wants
        }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")
        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String) {
        // TODO: Implement this method to send token to your app server.
    }

    private fun updateState(data: Map<String, String>) {
        // TODO:
    }

    override fun onCreate() {
        super.onCreate()
        // TODO:
    }

    companion object {
        private const val TAG = "FMService"
    }
}