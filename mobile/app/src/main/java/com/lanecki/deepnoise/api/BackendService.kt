package com.lanecki.deepnoise.api

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.work.*
import com.lanecki.deepnoise.R
import com.lanecki.deepnoise.utils.InjectionUtils
import com.lanecki.deepnoise.workers.FMSTokenUpdateWorker
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.CookieHandler
import java.net.CookieManager
import java.util.concurrent.TimeUnit


class BackendService(
    private val backendClient: BackendApi,
    private val responseHandler: ResponseHandler
) {

    suspend fun login(context: Context) {
        val sharedPreferences: SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(context)

        // TODO: keep in database (security?)
        val nickKey = context.resources.getString(R.string.settings_login)
        val passwordKey = context.resources.getString(R.string.settings_password)

        val nick = sharedPreferences.getString(nickKey, "") ?: ""
        val password = sharedPreferences.getString(passwordKey, "") ?: ""

        try {
            val response = backendClient.login(Credentials(nick, password))
        } catch (e: Exception) {
            Log.d(TAG,"Fucked with ${e}")
        }
    }

    fun scheduleUpdateToken(context: Context, token: String) {
        val inputData = workDataOf("token" to token)
        val updateTokenRequest = OneTimeWorkRequestBuilder<FMSTokenUpdateWorker>()
            .setInputData(inputData)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueue(updateTokenRequest)
    }

    suspend fun updateToken(token: String): Resource<Unit> {
        return try {
            val response = backendClient.updateToken(token)
            return responseHandler.handleSuccess(response)
        } catch (e: Exception) {
            responseHandler.handleException(e)
        }
    }

    companion object {
        private const val TAG = "BackendService";
        private const val URL = "192.168.100.106:5000"
        @Volatile
        private var backendServiceInstance: BackendService? = null

        fun getInstance(): BackendService {
            return backendServiceInstance ?: synchronized(this) {
                backendServiceInstance ?: buildBackendService().also {
                    backendServiceInstance = it
                }
            }
        }

        private fun buildBackendService(): BackendService {
            val interceptor = HttpLoggingInterceptor()
            interceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
            val cookieHandler: CookieHandler = CookieManager()

            val httpClient = OkHttpClient.Builder().addNetworkInterceptor(interceptor)
                .cookieJar(JavaNetCookieJar(cookieHandler))
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            val apiClient = Retrofit.Builder()
                .baseUrl("http://${URL}/")
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(BackendApi::class.java)

            val responseHandler = InjectionUtils.provideResponseHandler()
            return BackendService(apiClient, responseHandler)
        }
    }
}