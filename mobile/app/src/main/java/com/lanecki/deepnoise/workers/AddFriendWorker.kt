package com.lanecki.deepnoise.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lanecki.deepnoise.api.BackendService
import com.lanecki.deepnoise.api.Status
import com.lanecki.deepnoise.api.model.Token
import com.lanecki.deepnoise.db.AppDatabase
import com.lanecki.deepnoise.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AddFriendWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val login = inputData.getString("login")

        if (login != null) {
            val user = User(0, login)
            val userDao = AppDatabase.getInstance(appContext).userDao()
            userDao.insert(user)
            Result.success()
        } else {
            Log.d(TAG, "Login required.")
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "AddFriendWorker"
    }
}