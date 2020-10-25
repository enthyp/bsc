package com.lanecki.deepnoise.channel

import android.util.Log
import com.google.gson.Gson
import com.lanecki.deepnoise.api.WSApi
import com.lanecki.deepnoise.utils.Actor
import com.lanecki.deepnoise.utils.AuxLifecycle
import com.tinder.scarlet.Scarlet
import com.tinder.scarlet.WebSocket
import com.tinder.scarlet.messageadapter.gson.GsonMessageAdapter
import com.tinder.scarlet.retry.ExponentialWithJitterBackoffStrategy
import com.tinder.scarlet.websocket.okhttp.newWebSocketFactory
import com.tinder.streamadapter.coroutines.CoroutinesStreamAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import okhttp3.OkHttpClient
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.util.concurrent.TimeUnit


// TODO: handle exceptions (failed to connect, ...)
class WebSocketChannelClient(
    private val listener: Actor<Message>,
    private val serverAddress: String,
    private val lifecycle: AuxLifecycle
) : Actor<Message>(Dispatchers.IO) {

    companion object {
        private const val TAG = "WSChannelClient"
        private const val SERVER_ADDRESS = "http://192.168.100.106:5000"  // TODO: in Settings panel only
    }

    enum class State {
        INIT,
        CONNECTED,
        SIGNALLING,
        CLOSING,
        CLOSED
    }
    
    private val backoffStrategy = ExponentialWithJitterBackoffStrategy(5000, 5000)
    private val gson = Gson()
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val socket: WSApi
    private val receiveSignalChannel: ReceiveChannel<String>
    private val wsEventChannel: ReceiveChannel<WebSocket.Event>

    private val initConnected = CompletableDeferred<Unit>()
    private var state = State.INIT

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
        receiveSignalChannel = socket.receiveSignal()
    }

    suspend fun run() = withContext(dispatcher) {
        launch { receiveWSEvent() }
        initConnected.await()

        launch { receiveServerMsg() }
        receive { msg ->
            Log.d(TAG, "Received $msg in state $state")

            when(msg) {
                is ConnectedMsg -> handleConnected()
                is JoinRequestMsg -> handleJoinRequest(msg)
                is AcceptedMsg -> handleAccepted(msg)
                
                is ReceivedOfferMsg -> handleReceivedOffer(msg)
                is ReceivedAnswerMsg -> handleReceivedAnswer(msg)
                is ReceivedIceCandidateMsg -> handleReceivedIce(msg)

                is SentOfferMsg -> handleSentOffer(msg)
                is SentAnswerMsg -> handleSentAnswer(msg)
                is SentIceCandidateMsg -> handleSentIce(msg)

                is LeaveMsg -> handleLeave()

                is ErrorMsg -> handleError(msg)
                else -> handleOther(msg)
            }
        }
    }

    // WebSocket events

    private suspend fun receiveWSEvent() {
        loop@ for (msg in wsEventChannel) {
            when(msg) {
                is WebSocket.Event.OnConnectionOpened<*> -> onWSOpened()
                else -> onWSOther(msg)
            }
        }
    }

    private suspend fun onWSOpened() {
        if (state == State.INIT) {
            state = State.CONNECTED
            initConnected.complete(Unit)
        } else {
            send(ConnectedMsg)
        }
    }

    private suspend fun onWSOther(event: WebSocket.Event) {
        Log.d(TAG, "Received other WebSocket event: $event")
        // TODO: disconnected?
    }

    // Server messages

    private suspend fun receiveServerMsg() = withContext(dispatcher) {
        val receiveChannel = socket.receiveSignal()
        Log.d(TAG, "Receiving...")

        while (isActive) {
            try {
                val json = receiveChannel.receive()
                Log.d(TAG, "Got $json")

                withContext(Dispatchers.Default) {
                    Log.d(TAG, "Parsing msg")
                    val msg: ServerMessage = gson.fromJson(json, ServerMessage::class.java)
                    Log.d(TAG, "Dispatching msg")
                    dispatchMsg(msg)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Error while receiving from server: $e")
            }
        }
    }

    private suspend fun dispatchMsg(msg: ServerMessage) {
        Log.d(TAG, msg.type.toString())

        when (msg.type) {
            MessageType.OFFER -> send(gson.fromJson(msg.payload, ReceivedOfferMsg::class.java))
            MessageType.ANSWER -> send(gson.fromJson(msg.payload, ReceivedAnswerMsg::class.java))
            MessageType.ICE_CANDIDATE -> send(gson.fromJson(msg.payload, ReceivedIceCandidateMsg::class.java))
            MessageType.ACCEPTED -> send(gson.fromJson(msg.payload, AcceptedMsg::class.java))
            else -> send(ErrorMsg(msg.type.toString()))
        }
    }

    private suspend fun handleJoinRequest(msg: JoinRequestMsg) = withContext(dispatcher) {
        if (state == State.CONNECTED) {
            sendToServer(MessageType.JOIN, JoinMsg(msg.channelId))
            msg.response.complete(Unit)
        }
    }

    private suspend fun handleConnected() = withContext(dispatcher) {
        if (state == State.INIT) {
            state = State.CONNECTED
            launch { receiveServerMsg() }
        }

        // TODO: handle reconnects somehow?
    }

    private suspend fun handleAccepted(msg: AcceptedMsg) = withContext(dispatcher) {
        if (state == State.CONNECTED) {
            state = State.SIGNALLING
            listener.send(msg)
        }
    }

    private suspend fun handleLeave() = withContext(dispatcher) {
        if (state == State.SIGNALLING) {
            state = State.CLOSING
            sendToServer(MessageType.LEAVE, Unit)
        }
    }

    private suspend fun handleReceivedOffer(msg: ReceivedOfferMsg) = withContext(dispatcher) {
        if (state == State.SIGNALLING)
            listener.send(msg)
    }

    private suspend fun handleReceivedAnswer(msg: ReceivedAnswerMsg) = withContext(dispatcher) {
        if (state == State.SIGNALLING)
            listener.send(msg)
    }

    private suspend fun handleReceivedIce(msg: ReceivedIceCandidateMsg) = withContext(dispatcher) {
        if (state == State.SIGNALLING)
            listener.send(msg)
    }

    private suspend fun handleSentOffer(msg: SentOfferMsg) = withContext(dispatcher) {
        if (state == State.SIGNALLING)
            sendToServer(MessageType.OFFER, msg)
    }

    private suspend fun handleSentAnswer(msg: SentAnswerMsg) = withContext(dispatcher) {
        if (state == State.SIGNALLING)
            sendToServer(MessageType.ANSWER, msg)
    }

    private suspend fun handleSentIce(msg: SentIceCandidateMsg) = withContext(dispatcher) {
        if (state == State.SIGNALLING)
            sendToServer(MessageType.ICE_CANDIDATE, msg)
    }
    
    private suspend fun handleError(msg: ErrorMsg) {
        Log.d(TAG, "Received error msg $msg from server")
        // TODO: what else??
    }

    private suspend fun handleClose() {
        if (state == State.SIGNALLING || state == State.CLOSING) {
            state = State.CLOSED
            lifecycle.stop()
            // TODO: a response to wait on?
        } else {
            throw Exception("WSClient: CLOSE message received in state $state")
        }
    }

    private fun handleOther(msg: Message) {
        Log.d(TAG, "Received unhandled msg $msg from server")
        // TODO: what else??
    }

    private suspend fun sendToServer(type: MessageType, data: Any?) = withContext(Dispatchers.IO) {
        val jsonData = gson.toJson(data)
        val msg = ServerMessage(type, jsonData)
        val json = gson.toJson(msg)
        socket.sendSignal(json)
        Log.d(TAG, "Sent: $json")
        // TODO: use GsonMessageAdapter for serialization somehow
        return@withContext
    }

    // Generic wrapper for messages exchanged with server
    data class ServerMessage(val type: MessageType, val payload: String)
}

enum class MessageType {
    JOIN,
    ACCEPTED,
    OFFER,
    ANSWER,
    ICE_CANDIDATE,
    LEAVE,
    JOINED,
    LEFT
}
