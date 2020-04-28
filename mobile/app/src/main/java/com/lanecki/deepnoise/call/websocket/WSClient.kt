package com.lanecki.deepnoise.call.websocket

import android.util.Log
import com.google.gson.Gson
import com.tinder.scarlet.Lifecycle
import com.tinder.scarlet.Scarlet
import com.tinder.scarlet.WebSocket
import com.tinder.scarlet.messageadapter.gson.GsonMessageAdapter
import com.tinder.scarlet.retry.ExponentialWithJitterBackoffStrategy
import com.tinder.scarlet.websocket.okhttp.newWebSocketFactory
import com.tinder.streamadapter.coroutines.CoroutinesStreamAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import okhttp3.OkHttpClient
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.util.concurrent.TimeUnit

interface WSClientI {
    suspend fun send(type: MsgType, data: Any?)
    suspend fun receive(listener: WSListener)
}

// TODO: handle exceptions (failed to connect, ...)
class WSClient(
    private val serverAddress: String,
    private val lifecycle: Lifecycle
) : WSClientI,
    CoroutineScope by CoroutineScope(Dispatchers.IO) {

    private val backoffStrategy = ExponentialWithJitterBackoffStrategy(5000, 5000)
    private val gson = Gson()
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val socket: WSApi
    private val sendChannel = Channel<Pair<MsgType, Any?>>()
    private val wsEventChannel: ReceiveChannel<WebSocket.Event>

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

        wsEventChannel = socket.receiveWebSocketEvent()
    }

    suspend fun ensureOpened() {
        // TODO: I really should make all this event-driven, this sucks!

        loop@ while (true) {
            when(wsEventChannel.receive()) {
                is WebSocket.Event.OnConnectionOpened<*> -> break@loop
                else -> continue@loop
            }
        }
    }

    override suspend fun send(type: MsgType, data: Any?) = withContext(this.coroutineContext) {
        val jsonData = gson.toJson(data)
        val msg = WSMessage(type, jsonData)
        val json = gson.toJson(msg)
        socket.sendSignal(json)
        // TODO: use GsonMessageAdapter for serialization somehow
    }

    override suspend fun receive(listener: WSListener) = withContext(this.coroutineContext) {
        val receiveChannel = socket.receiveSignal()

        while (true) {
            val json = receiveChannel.receive()

            withContext(Dispatchers.Default) {
                val msg: WSMessage = gson.fromJson(json, WSMessage::class.java)
                dispatchMsg(msg, listener)
            }
        }
    }

    private suspend fun dispatchMsg(msg: WSMessage, listener: WSListener) {
        when (msg.type) {
            MsgType.OFFER -> {
                val desc = gson.fromJson(msg.payload, SessionDescription::class.java)
                listener.onOffer(desc)
            }
            MsgType.ANSWER -> {
                val desc = gson.fromJson(msg.payload, SessionDescription::class.java)
                listener.onAnswer(desc)
            }
            MsgType.ICE_CANDIDATE -> {
                val ice = gson.fromJson(msg.payload, IceCandidate::class.java)
                listener.onIceCandidate(ice)
            }
            MsgType.ACCEPTED -> {
                val acc = gson.fromJson(msg.payload, AcceptedMsg::class.java)
                listener.onAccepted(acc)
            }
            MsgType.REFUSED -> {
                val ref = gson.fromJson(msg.payload, RefusedMsg::class.java)
                listener.onRefused(ref)
            }
            else -> listener.onError(msg.type)
        }
    }

    companion object {
        private const val SERVER_ADDRESS = "ws://192.168.100.106:5000"  // TODO: in Settings panel only
    }
}

enum class MsgType {
    CALL,
    ACCEPT,
    ACCEPTED,
    REFUSED,
    OFFER,
    ANSWER,
    ICE_CANDIDATE
}

data class WSMessage(val type: MsgType, val payload: String)
data class CallMsg(val from: String, val to: String)
data class AcceptMsg(val from: String, val to: String)
data class AcceptedMsg(val from: String, val to: String)
data class RefusedMsg(val from: String, val to: String)
