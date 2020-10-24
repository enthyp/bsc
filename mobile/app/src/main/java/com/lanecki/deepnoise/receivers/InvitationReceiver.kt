package com.lanecki.deepnoise.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.lanecki.deepnoise.Constants
import com.lanecki.deepnoise.workers.AnswerInvitationWorker
import java.util.concurrent.TimeUnit

class InvitationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val who = intent?.getStringExtra(Constants.EXTRA_WHO_INVITES)
        val notificationId = intent?.getIntExtra(Constants.EXTRA_NOTIFICATION_ID, -1)

        if (who != null && notificationId != null && context != null ) {
            when (intent.action) {
                Constants.ACTION_ACCEPT_INVITATION -> handleAccept(who, context)
                Constants.ACTION_REFUSE_INVITATION -> handleRefuse(who, context)
            }

            if (notificationId < 0) return

            // TODO: cancel when successfully notified server
            with(NotificationManagerCompat.from(context)) {
                cancel(Constants.NOTIFICATION_TAG_INVITATION, notificationId)
            }

        }
    }

    private fun handleAccept(to: String, context: Context) {
        val inputData = workDataOf("to" to to, "positive" to true)
        val answerInvitationRequest = OneTimeWorkRequestBuilder<AnswerInvitationWorker>()
            .setInputData(inputData)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueue(answerInvitationRequest)
    }

    private fun handleRefuse(to: String, context: Context) {
        val inputData = workDataOf("to" to to, "positive" to false)
        val answerInvitationRequest = OneTimeWorkRequestBuilder<AnswerInvitationWorker>()
            .setInputData(inputData)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueue(answerInvitationRequest)
    }
}