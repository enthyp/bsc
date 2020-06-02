package com.lanecki.deepnoise.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lanecki.deepnoise.api.BackendService
import com.lanecki.deepnoise.api.Resource
import com.lanecki.deepnoise.api.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FMSTokenUpdateWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val token = inputData.getString("token")

        if (token != null) {
            val backendService = BackendService.getInstance()
            val response = backendService.updateToken(token)

            when (response.status) {
                Status.SUCCESS -> return@withContext Result.success()
                else -> onFailure(response, backendService, appContext)
            }

        } else {
            Log.d(TAG, "Token required.")
            Result.failure()
        }
    }

    private suspend fun onFailure(
        response: Resource<Unit>,
        backendService: BackendService,
        appContext: Context
    ): Result =
        when (response.errorCode) {
            401 -> {
                backendService.login(appContext)
                Result.retry()
            }
            else -> Result.failure()
        }

    companion object {
        private const val TAG = "FMSTokenUpdateWorker"
    }
}