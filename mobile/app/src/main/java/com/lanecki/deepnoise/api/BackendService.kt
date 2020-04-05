package com.lanecki.deepnoise.api

import com.lanecki.deepnoise.utils.InjectionUtils
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class BackendService(
    private val backendClient: BackendApi,
    private val responseHandler: ResponseHandler
) {

    suspend fun updateToken(token: Token): Resource<Unit> {
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

        fun newInstance(): BackendService {
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