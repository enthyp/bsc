package com.lanecki.deepnoise


import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.lanecki.deepnoise.call.CallState
import com.lanecki.deepnoise.workers.UpdateFCMTokenWorker
import java.util.concurrent.TimeUnit

class FMService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        if (remoteMessage.data.isNotEmpty()) {
            when (remoteMessage.data["type"]) {
                "INCOMING" -> notifyIncomingCall(remoteMessage.data)
                "INVITATION" -> notifyFriendsInvitation(remoteMessage.data)
                else -> Log.d(TAG, "Unknown message type: $this")
            }
        }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed FCM token")
        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String) {
        val inputData = workDataOf("token" to token)
        val updateTokenRequest = OneTimeWorkRequestBuilder<UpdateFCMTokenWorker>()
            .setInputData(inputData)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(this).enqueue(updateTokenRequest)
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

    private fun notifyFriendsInvitation(data: MutableMap<String, String>) {
        val from = data["from_user"]

        // TODO: use BroadcastReceiver to handle click (Accept/Refuse) actions
        val intent = Intent()
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, 0)

        val channelId = "default"
        val builder =
            NotificationCompat.Builder(this, channelId)
                .setDefaults(Notification.DEFAULT_ALL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("You received a friends request!")
                .setContentText("From: $from")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

        // TODO: will do for now
        val oneTimeID = SystemClock.uptimeMillis()
        with(NotificationManagerCompat.from(this)) {
            notify("FRIENDS_INVITATION", oneTimeID.toInt(), builder.build())
        }

    }

    companion object {
        private const val TAG = "FMService"
    }
}