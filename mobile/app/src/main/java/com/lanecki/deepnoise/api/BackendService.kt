package com.lanecki.deepnoise.api

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import androidx.preference.PreferenceManager
import androidx.work.*
import com.lanecki.deepnoise.R
import com.lanecki.deepnoise.api.model.Credentials
import com.lanecki.deepnoise.api.model.InvitationAnswer
import com.lanecki.deepnoise.api.model.Token
import com.lanecki.deepnoise.model.User
import com.lanecki.deepnoise.utils.InjectionUtils
import com.lanecki.deepnoise.workers.UpdateFCMTokenWorker
import com.tinder.scarlet.Scarlet
import com.tinder.scarlet.messageadapter.gson.GsonMessageAdapter
import com.tinder.scarlet.retry.ExponentialWithJitterBackoffStrategy
import com.tinder.scarlet.websocket.okhttp.newWebSocketFactory
import com.tinder.streamadapter.coroutines.CoroutinesStreamAdapterFactory
import kotlinx.coroutines.Dispatchers
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

    suspend fun login(context: Context): Resource<Unit> {
        val sharedPreferences: SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(context)

        // TODO: use Google Sign-in (token-based auth, this is unsafe)
        // NOTE: SyncAdapter interface
        val nickKey = context.resources.getString(R.string.settings_login)
        val passwordKey = context.resources.getString(R.string.settings_password)

        val nick = sharedPreferences.getString(nickKey, "") ?: ""
        val password = sharedPreferences.getString(passwordKey, "") ?: ""

        return try {
            val response = backendClient.login(
                Credentials(
                    nick,
                    password
                )
            )
            responseHandler.handleSuccess(response)
        } catch (e: Exception) {
            responseHandler.handleException(e)
        }
    }

    suspend fun updateToken(token: Token): Resource<Unit> {
        return try {
            val response = backendClient.updateToken(token)
            return responseHandler.handleSuccess(response)
        } catch (e: Exception) {
            responseHandler.handleException(e)
        }
    }

    fun findUsers(query: String): LiveData<List<User>> {
        return liveData(Dispatchers.IO) {
            val users = backendClient.getUsers(query)
            emit(users)
        }
    }

    suspend fun getFriendsForSelf(): Resource<List<User>> {
        return try {
            val response = backendClient.getFriendsForSelf()
            return responseHandler.handleSuccess(response)
        } catch (e: Exception) {
            responseHandler.handleException(e)
        }
    }

    fun invite(user: User): LiveData<Resource<Unit>> {
        return liveData(Dispatchers.IO) {
            try {
                val response = backendClient.inviteUser(user)
                emit(responseHandler.handleSuccess(response))
            } catch (e: Exception) {
                emit(responseHandler.handleException(e))
            }
        }
    }

    suspend fun answerInvitation(to: User, positive: Boolean): Resource<Unit> {
        return try {
            val response = backendClient.answerInvitation(InvitationAnswer(to, positive))
            return responseHandler.handleSuccess(response)
        } catch (e: Exception) {
            responseHandler.handleException(e)
        }
    }

    companion object {
        private const val TAG = "BackendService";
        private const val URL = "192.168.100.106:5000"

        @Volatile
        private var httpClient: OkHttpClient? = null

        @Volatile
        private var backendServiceInstance: BackendService? = null

        @Volatile
        private var wsApiInstance: WSApi? = null

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

            httpClient = OkHttpClient.Builder().addNetworkInterceptor(interceptor)
                .cookieJar(JavaNetCookieJar(cookieHandler))
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            val apiClient = Retrofit.Builder()
                .baseUrl("http://${URL}/")
                .client(httpClient!!)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(BackendApi::class.java)

            val responseHandler = InjectionUtils.provideResponseHandler()
            return BackendService(apiClient, responseHandler)
        }

        // TODO: quite ugly
        fun getWSInstance(): WSApi {
            return wsApiInstance ?: synchronized(this) {
                wsApiInstance ?: buildWSApiInstance().also {
                    wsApiInstance = it
                }
            }
        }

        private fun buildWSApiInstance(): WSApi {
            val backoffStrategy = ExponentialWithJitterBackoffStrategy(5000, 5000)

            return Scarlet.Builder()
                .webSocketFactory(httpClient!!.newWebSocketFactory("http://${URL}/channels")) // TODO: wss?
                .addMessageAdapterFactory(GsonMessageAdapter.Factory())
                .addStreamAdapterFactory(CoroutinesStreamAdapterFactory())
                .backoffStrategy(backoffStrategy)
                .build()
                .create(WSApi::class.java)
        }
    }
}