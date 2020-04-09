package com.lanecki.deepnoise


import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.lanecki.deepnoise.workers.FMSTokenUpdateWorker

class FMService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        if (remoteMessage.data.isNotEmpty()) {
            when (remoteMessage.data["type"]) {
                "incoming call" -> notifyIncomingCall(remoteMessage.data)
                else -> Log.d(TAG, "Unknown message type: $this")
            }
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

    private fun notifyIncomingCall(data: MutableMap<String, String>) {
        val caller = data["caller"]

        val intent = Intent(this, CallActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channelId = "default"
        val notification =
            NotificationCompat.Builder(this, channelId)
                .setDefaults(Notification.DEFAULT_ALL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Incoming call")
                .setContentText("$caller calls")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(pendingIntent, true)
                .build()

        startForeground(1, notification)  // TODO: 1?
    }

    companion object {
        private const val TAG = "FMService"
    }
}