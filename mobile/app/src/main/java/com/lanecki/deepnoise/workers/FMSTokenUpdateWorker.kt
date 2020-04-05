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

class FMSTokenUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters)
    : CoroutineWorker(appContext, workerParams) {

    // TODO: return Result.retry() on error?
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val identity = inputData.getString("identity")
        val token = inputData.getString("token")

        if (identity != null && token != null) {
            val backendService = BackendService.newInstance()
            val response = backendService.updateToken(Token(identity, token))

            Log.d(TAG, response.message.toString())
            when (response.status) {
                Status.SUCCESS -> Result.success()
                else -> Result.failure()
            }
        }
        else {
            Log.d(TAG, "Both identity and token required.")
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "FMSTokenUpdateWorker"
    }
}