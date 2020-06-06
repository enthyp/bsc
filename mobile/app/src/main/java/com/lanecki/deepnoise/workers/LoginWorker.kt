package com.lanecki.deepnoise.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lanecki.deepnoise.api.BackendService
import com.lanecki.deepnoise.api.Status
import com.lanecki.deepnoise.utils.InjectionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LoginWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val backendService = InjectionUtils.provideBackendService()
        val response = backendService.login(appContext)

        when (response.status) {
            Status.SUCCESS -> Result.success()
            else -> Result.failure()
        }
    }

    companion object {
        private const val TAG = "FMSTokenUpdateWorker"
    }
}