package com.lanecki.deepnoise.call

import com.google.gson.Gson
import com.tinder.scarlet.Lifecycle
import com.tinder.scarlet.Scarlet
import com.tinder.scarlet.messageadapter.gson.GsonMessageAdapter
import com.tinder.scarlet.retry.ExponentialWithJitterBackoffStrategy
import com.tinder.scarlet.websocket.okhttp.newWebSocketFactory
import com.tinder.streamadapter.coroutines.CoroutinesStreamAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.util.concurrent.TimeUnit

// TODO: handle exceptions (failed to connect, ...)
class WSClient(
    private val serverAddress: String,
    private val listener: SignallingListener,
    private val lifecycle: Lifecycle
) :
    CoroutineScope by CoroutineScope(Dispatchers.IO) {

    private val backoffStrategy = ExponentialWithJitterBackoffStrategy(5000, 5000)
    private val gson = Gson()
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val socket: WSApi
    private val receiveChannel: ReceiveChannel<Any>

    init {
        val address = if (serverAddress != "") serverAddress else SERVER_ADDRESS

        socket = Scarlet.Builder()
            .webSocketFactory(httpClient.newWebSocketFactory(address))
            .addMessageAdapterFactory(GsonMessageAdapter.Factory())
            .addStreamAdapterFactory(CoroutinesStreamAdapterFactory())
            .backoffStrategy(backoffStrategy)
            .lifecycle(lifecycle)
            .build()
            .create()

        receiveChannel = socket.receiveSignal()
    }

    fun send(type: String, data: Any) = launch {
        val jsonData = gson.toJson(data)
        val msg = WSMessage(type, jsonData)
        val json = gson.toJson(msg)
        socket.sendSignal(json)
    }

    fun receive() = launch {
        while (true) {
            val json = receiveChannel.receive() as String
            val msg = gson.fromJson(json, WSMessage::class.java)
            val jsonPayload = msg.payload

            when (msg.type) {
                ICE_CANDIDATE -> {
                    val candidate = gson.fromJson(jsonPayload, IceCandidate::class.java)
                    withContext(Dispatchers.Main) {
                        listener.onIceCandidateReceived(candidate)
                    }
                }
                OFFER -> {
                    val desc = gson.fromJson(jsonPayload, SessionDescription::class.java)
                    withContext(Dispatchers.Main) {
                        listener.onOfferReceived(desc)
                    }
                }
                ANSWER -> {
                    val answer = gson.fromJson(jsonPayload, SessionDescription::class.java)
                    withContext(Dispatchers.Main) {
                        listener.onAnswerReceived(answer)
                    }
                }
            }
        }
    }

    companion object {
        private const val SERVER_ADDRESS = "ws://192.168.100.106:5000"  // TODO: in Settings panel

        private const val SIGNAL_TYPE = "signal_type"
        private const val PAYLOAD = "payload"
        const val OFFER = "offer"
        const val ANSWER = "answer"
        const val ICE_CANDIDATE = "ice_candidate"
    }
}