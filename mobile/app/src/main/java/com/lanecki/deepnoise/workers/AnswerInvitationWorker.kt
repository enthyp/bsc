package com.lanecki.deepnoise.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lanecki.deepnoise.api.BackendService
import com.lanecki.deepnoise.api.Status
import com.lanecki.deepnoise.db.AppDatabase
import com.lanecki.deepnoise.model.User
import com.lanecki.deepnoise.utils.InjectionUtils
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

        val user = User(0, to)
        val backendService = InjectionUtils.provideBackendService()
        val response = backendService.answerInvitation(user, positive)

        when (response.status) {
            Status.SUCCESS -> {
                onAnswered(user, positive)
                Result.success()
            }
            else -> Result.failure()
        }
    }

    private suspend fun onAnswered(user: User, positive: Boolean) {
        if (positive) {
            val userDao = AppDatabase.getInstance(appContext).userDao()
            userDao.insert(user)
        }
    }

    companion object {
        private const val TAG = "AnswerInvitationWorker"
    }
}