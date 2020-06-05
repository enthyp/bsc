package com.lanecki.deepnoise.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.lanecki.deepnoise.Constant

class InvitationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val from = intent?.getStringExtra(Constant.EXTRA_WHO_INVITES)
        val notificationId = intent?.getIntExtra(Constant.EXTRA_NOTIFICATION_ID, -1)

        if (from != null && notificationId != null && context != null ) {
            when (intent.action) {
                Constant.ACTION_ACCEPT_INVITATION -> handleAccept(from, context)
                Constant.ACTION_REFUSE_INVITATION -> handleRefuse(from, context)
            }

            if (notificationId < 0) return

            with(NotificationManagerCompat.from(context)) {
                cancel(Constant.NOTIFICATION_TAG_INVITATION, notificationId)
            }

        }
    }

    private fun handleAccept(from: String, context: Context) {
        Log.d("RECV", "Acc")
    }

    private fun handleRefuse(from: String, context: Context) {
        Log.d("RECV", "Ref")
    }
}