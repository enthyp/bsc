package com.lanecki.deepnoise.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lanecki.deepnoise.api.BackendService
import com.lanecki.deepnoise.api.Status
import com.lanecki.deepnoise.api.Token
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateFCMTokenWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val token = inputData.getString("token")

        if (token != null) {
            val backendService = BackendService.getInstance()
            val response = backendService.updateToken(Token(token))

            when (response.status) {
                Status.SUCCESS -> Result.success()
                else -> Result.failure()
            }
        } else {
            Log.d(TAG, "Token required.")
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "UpdateFCMTokenWorker"
    }
}