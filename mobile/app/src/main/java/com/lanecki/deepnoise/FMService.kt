package com.lanecki.deepnoise


import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.lanecki.deepnoise.api.BackendService
import com.lanecki.deepnoise.call.CallState

class FMService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        if (remoteMessage.data.isNotEmpty()) {
            when (remoteMessage.data["type"]) {
                "INCOMING" -> notifyIncomingCall(remoteMessage.data)
                else -> Log.d(TAG, "Unknown message type: $this")
            }
        }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")
        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String) {
        val backendService = BackendService.getInstance()
        backendService.scheduleUpdateToken(this, token)
    }

    private fun notifyIncomingCall(data: MutableMap<String, String>) {
        val caller = data["caller"]
        val callId = data["call_id"]

        // TODO: give the user some choice. Also, it's no good on the tablet...
        val intent = Intent(this, CallActivity::class.java).apply {
            putExtra(Constant.CALLEE_KEY, caller)
            putExtra(Constant.CALL_ID_KEY, callId)
            putExtra(Constant.INITIAL_STATE_KEY, CallState.INCOMING)
        }
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