package com.lanecki.deepnoise.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lanecki.deepnoise.api.BackendService
import com.lanecki.deepnoise.api.Status
import com.lanecki.deepnoise.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AnswerInvitationWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val to = inputData.getString("to")
        val positive = inputData.getBoolean("positive", false)

        if (to == null) return@withContext Result.failure()

        val backendService = BackendService.getInstance()
        val response = backendService.answerInvitation(User(to), positive)

        when (response.status) {
            Status.SUCCESS -> Result.success()
            else -> Result.failure()
        }
    }

    companion object {
        private const val TAG = "AnswerInvitationWorker"
    }
}