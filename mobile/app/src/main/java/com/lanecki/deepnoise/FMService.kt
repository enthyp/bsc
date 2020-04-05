package com.lanecki.deepnoise

import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.lanecki.deepnoise.workers.FMSTokenUpdateWorker

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
        // TODO: handle constants better
        val inputData = workDataOf("identity" to "client", "token" to token)
        val updateTokenRequest = OneTimeWorkRequestBuilder<FMSTokenUpdateWorker>()
            .setInputData(inputData)
            .build()
        WorkManager.getInstance(this).enqueue(updateTokenRequest)
    }

    private fun updateState(data: Map<String, String>) {
        // TODO:
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created.")
    }

    companion object {
        private const val TAG = "FMService"
    }
}