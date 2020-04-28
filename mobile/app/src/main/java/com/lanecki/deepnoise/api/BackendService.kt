package com.lanecki.deepnoise.api

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.lanecki.deepnoise.R
import com.lanecki.deepnoise.utils.InjectionUtils
import com.lanecki.deepnoise.workers.FMSTokenUpdateWorker
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class BackendService(
    private val backendClient: BackendApi,
    private val responseHandler: ResponseHandler
) {

    fun scheduleTokenUpdate(context: Context, token: String) {
        val sharedPreferences: SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(context)

        val nickKey = context.resources.getString(R.string.settings_nick)
        val nick = sharedPreferences.getString(nickKey, "") ?: ""

        val inputData = workDataOf("identity" to nick, "token" to token)
        val updateTokenRequest = OneTimeWorkRequestBuilder<FMSTokenUpdateWorker>()
            .setInputData(inputData)
            .build()
        WorkManager.getInstance(context).enqueue(updateTokenRequest)
    }

    suspend fun scheduleTokenUpdate(token: Token): Resource<Unit> {
        return try {
            val response = backendClient.updateToken(token)
            return responseHandler.handleSuccess(response)
        } catch (e: Exception) {
            responseHandler.handleException(e)
        }
    }

    companion object {
        private const val URL = "192.168.100.106:5000"
        @Volatile private var backendServiceInstance: BackendService? = null

        fun getInstance(): BackendService {
            return backendServiceInstance ?: synchronized(this) {
                backendServiceInstance ?: buildBackendService().also {
                    backendServiceInstance = it
                }
            }
        }

        private fun buildBackendService(): BackendService {
            val apiClient = Retrofit.Builder()
                .baseUrl("http://${URL}/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(BackendApi::class.java)

            val responseHandler = InjectionUtils.provideResponseHandler()
            return BackendService(apiClient, responseHandler)
        }
    }
}