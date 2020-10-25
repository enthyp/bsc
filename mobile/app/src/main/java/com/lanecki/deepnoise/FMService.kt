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
import com.lanecki.deepnoise.receivers.InvitationReceiver
import com.lanecki.deepnoise.workers.AddFriendWorker
import com.lanecki.deepnoise.workers.UpdateFCMTokenWorker
import java.util.concurrent.TimeUnit

class FMService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        if (remoteMessage.data.isNotEmpty()) {
            when (remoteMessage.data["type"]) {
                "INCOMING" -> notifyIncomingCall(remoteMessage.data)
                "INVITATION" -> notifyFriendsInvitation(remoteMessage.data)
                "INVITATION_ANSWER" -> notifyFriendsInvitationAnswer(remoteMessage.data)
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
            putExtra(Constants.CALLEE_KEY, caller)
            putExtra(Constants.CALL_ID_KEY, callId)
            putExtra(Constants.INITIAL_STATE_KEY, CallState.INCOMING)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        // TODO: constants
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
        // TODO: will do for now
        val notificationId = SystemClock.uptimeMillis().toInt()

        val acceptIntent = Intent(this, InvitationReceiver::class.java).apply {
            action = Constants.ACTION_ACCEPT_INVITATION
            putExtra(Constants.EXTRA_WHO_INVITES, from)
            putExtra(Constants.EXTRA_NOTIFICATION_ID, notificationId)
        }

        val refuseIntent = Intent(this, InvitationReceiver::class.java).apply {
            action = Constants.ACTION_REFUSE_INVITATION
            putExtra(Constants.EXTRA_WHO_INVITES, from)
            putExtra(Constants.EXTRA_NOTIFICATION_ID, notificationId)
        }

        val acceptPendingIntent: PendingIntent = PendingIntent.getBroadcast(this, notificationId, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val refusePendingIntent: PendingIntent = PendingIntent.getBroadcast(this, notificationId, refuseIntent, PendingIntent.FLAG_UPDATE_CURRENT)

        // TODO: constants
        val channelId = "default"
        val builder =
            NotificationCompat.Builder(this, channelId)
                .setDefaults(Notification.DEFAULT_ALL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Friends request")
                .setContentText("User $from wants to be friends")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .addAction(0, getString(R.string.invitation_accept), acceptPendingIntent)
                .addAction(0, getString(R.string.invitation_refuse), refusePendingIntent)
                .setAutoCancel(true)


        with(NotificationManagerCompat.from(this)) {
            notify(Constants.NOTIFICATION_TAG_INVITATION, notificationId, builder.build())
        }
    }

    private fun notifyFriendsInvitationAnswer(data: MutableMap<String, String>) {
        val from = data["from_user"]
        val positive = data["positive"] == "True"

        if (positive) {
            val inputData = workDataOf("login" to from)
            val addFriendRequest = OneTimeWorkRequestBuilder<AddFriendWorker>()
                .setInputData(inputData)
                .build()
            WorkManager.getInstance(this).enqueue(addFriendRequest)
        }

        // TODO: constants
        val contentMsg = "User $from " + (if (positive) "accepted" else "rejected") + " your invitation!"

        // TODO: will do for now
        val notificationId = SystemClock.uptimeMillis().toInt()

        val intent = Intent()
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, notificationId, intent, 0)

        val channelId = "default"
        val builder =
            NotificationCompat.Builder(this, channelId)
                .setDefaults(Notification.DEFAULT_ALL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Friends response")
                .setContentText(contentMsg)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            notify(Constants.NOTIFICATION_TAG_INVITATION, notificationId, builder.build())
        }
    }

    companion object {
        private const val TAG = "FMService"
    }
}