package com.lanecki.deepnoise.channel

import android.util.Log
import com.google.gson.Gson
import com.lanecki.deepnoise.call.CallWSMessageType
import com.lanecki.deepnoise.call.CallWSState
import com.lanecki.deepnoise.api.WSApi
import com.lanecki.deepnoise.utils.*
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


enum class WSState {
    INIT,
    CONNECTED,
    DISCONNECTED,
    RENDEZVOUS,
    LOGGED_IN,
    SIGNALLING,
    CLOSING,
    CLOSED
}

// TODO: handle exceptions (failed to connect, ...)
class WebSocketChannelClient(
    private val listener: Actor<Message>,
    private val serverAddress: String,
    private val lifecycle: AuxLifecycle
) : Actor<Message>(Dispatchers.IO) {

    companion object {
        private const val TAG = "WSClient"
        private const val SERVER_ADDRESS = "http://192.168.100.106:5000"  // TODO: in Settings panel only
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
    private var state = CallWSState.INIT

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
                is LoginMsg -> handleLogin(msg)
                is CallMsg -> handleCall(msg)
                is AcceptMsg -> handleAccept(msg)
                is RefuseMsg -> handleRefuse(msg)
                is HangupMsg -> handleHangup()
                is CancelMsg -> handleCancel()
                is HungUpMsg -> handleHungUp(msg)
                is CancelledMsg -> handleCancelled(msg)
                is AcceptedMsg -> handleAccepted(msg)
                is RefusedMsg -> handleRefused(msg)
                is OfferMsg -> handleOffer(msg)
                is AnswerMsg -> handleAnswer(msg)
                is IceCandidateMsg -> handleIce(msg)
                is ErrorMsg -> handleError(msg)
                is CloseMsg -> handleClose()
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
        if (state == CallWSState.INIT) {
            state = CallWSState.CONNECTED
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
            CallWSMessageType.OFFER -> {
                val desc = gson.fromJson(msg.payload, SessionDescription::class.java)
                send(OfferMsg(desc, false))
            }
            CallWSMessageType.ANSWER -> {
                val desc = gson.fromJson(msg.payload, SessionDescription::class.java)
                send(AnswerMsg(desc, false))
            }
            CallWSMessageType.ICE_CANDIDATE -> {
                val ice = gson.fromJson(msg.payload, IceCandidate::class.java)
                send(IceCandidateMsg(ice, false))
            }
            CallWSMessageType.ACCEPTED -> {
                val acc = gson.fromJson(msg.payload, AcceptedMsg::class.java)
                send(acc)
            }
            CallWSMessageType.REFUSED -> {
                val ref = gson.fromJson(msg.payload, RefusedMsg::class.java)
                send(ref)
            }
            CallWSMessageType.CANCELLED -> {
                val can = gson.fromJson(msg.payload, CancelledMsg::class.java)
                send(can)
            }
            CallWSMessageType.HUNG_UP -> {
                val hun = gson.fromJson(msg.payload, HungUpMsg::class.java)
                send(hun)
            }
            else -> send(ErrorMsg(msg.type.toString()))
        }
    }

    private suspend fun handleConnected() = withContext(dispatcher) {
        if (state == CallWSState.INIT) {
            state = CallWSState.CONNECTED
            launch { receiveServerMsg() }
        }

        // TODO: handle reconnects somehow?
    }

    private suspend fun handleLogin(msg: LoginMsg) = withContext(dispatcher) {
        if (state == CallWSState.CONNECTED) {
            state = CallWSState.LOGGED_IN
            sendToServer(
                CallWSMessageType.LOGIN,
                Login(msg.nickname)
            )
            msg.response.complete(Unit)
            // TODO: handle errors? will be replaced by some other authentication?
            // maybe ordinary HTTP sign-in can get us some token we could use here?
        }
    }

    private suspend fun handleCall(msg: CallMsg) = withContext(dispatcher) {
        if (state == CallWSState.LOGGED_IN) {
            state = CallWSState.RENDEZVOUS
            sendToServer(
                CallWSMessageType.CALL,
                Call(
                    msg.from,
                    msg.to
                )
            )
        }
    }

    private suspend fun handleAccept(msg: AcceptMsg) = withContext(dispatcher) {
        if (state == CallWSState.LOGGED_IN) {
            state = CallWSState.SIGNALLING
            sendToServer(
                CallWSMessageType.ACCEPT,
                Accept(
                    msg.from,
                    msg.to,
                    msg.callId
                )
            )
        }
    }

    private suspend fun handleRefuse(msg: RefuseMsg) = withContext(dispatcher) {
        if (state == CallWSState.LOGGED_IN) {
            state = CallWSState.CLOSING
            sendToServer(
                CallWSMessageType.REFUSE,
                Refuse(
                    msg.from,
                    msg.to,
                    msg.callId
                )
            )
        }
    }

    private suspend fun handleCancel() = withContext(dispatcher) {
        if (state == CallWSState.RENDEZVOUS) {
            state = CallWSState.CLOSING
            sendToServer(CallWSMessageType.CANCEL, Unit)
        }
    }

    private suspend fun handleCancelled(msg: CancelledMsg) = withContext(dispatcher) {
        if (state == CallWSState.LOGGED_IN || state == CallWSState.SIGNALLING) {
            state = CallWSState.CLOSING
            listener.send(msg)
        }
    }

    private suspend fun handleHangup() = withContext(dispatcher) {
        if (state == CallWSState.SIGNALLING) {
            state = CallWSState.CLOSING
            sendToServer(CallWSMessageType.HANGUP, Unit)
        }
    }

    private suspend fun handleHungUp(msg: HungUpMsg) = withContext(dispatcher) {
        if (state == CallWSState.SIGNALLING) {
            state = CallWSState.CLOSING
            listener.send(msg)
        }
    }

    private suspend fun handleAccepted(msg: AcceptedMsg) = withContext(dispatcher) {
        if (state == CallWSState.RENDEZVOUS) {
            state = CallWSState.SIGNALLING
            listener.send(msg)
        }
    }

    private suspend fun handleRefused(msg: RefusedMsg) = withContext(dispatcher) {
        if (state == CallWSState.RENDEZVOUS) {
            state = CallWSState.CLOSING
            listener.send(msg)
        }
    }

    private suspend fun handleOffer(msg: OfferMsg) = withContext(dispatcher) {
        if (state == CallWSState.SIGNALLING) {
            if (msg.out) {
                sendToServer(CallWSMessageType.OFFER, msg.sessionDescription)
            }
            else {
                listener.send(msg)
            }
        }
    }

    private suspend fun handleAnswer(msg: AnswerMsg) = withContext(dispatcher) {
        if (state == CallWSState.SIGNALLING) {
            if (msg.out) {
                sendToServer(CallWSMessageType.ANSWER, msg.sessionDescription)
            }
            else {
                listener.send(msg)
            }
        }
    }

    private suspend fun handleIce(msg: IceCandidateMsg) = withContext(dispatcher) {
        if (state == CallWSState.SIGNALLING) {
            if (msg.out) {
                sendToServer(CallWSMessageType.ICE_CANDIDATE, msg.iceCandidate)
            }
            else {
                listener.send(msg)
            }
        }
    }

    private suspend fun handleError(msg: ErrorMsg) {
        Log.d(TAG, "Received error msg $msg from server")
        // TODO: what else??
    }

    private suspend fun handleClose() {
        if (state == CallWSState.SIGNALLING || state == CallWSState.CLOSING) {
            state = CallWSState.CLOSED
            lifecycle.stop()
            // TODO: a response to wait on?
        } else {
            throw Exception("WSClient: CLOSE message received in state $state")
        }
    }


    private suspend fun handleOther(msg: Message) {
        Log.d(TAG, "Received unhandled msg $msg from server")
        // TODO: what else??
    }

    private suspend fun sendToServer(type: CallWSMessageType, data: Any?) = withContext(Dispatchers.IO) {
        val jsonData = gson.toJson(data)
        val msg =
            ServerMessage(
                type,
                jsonData
            )
        val json = gson.toJson(msg)
        socket.sendSignal(json)
        Log.d(TAG, "Sent: $json")
        // TODO: use GsonMessageAdapter for serialization somehow
        return@withContext
    }

    // Generic wrapper for other messages
    data class ServerMessage(val type: CallWSMessageType, val payload: String)

    // Actual messages (payload)
    // TODO: do we need all these types??
    data class Login(val nick: String)
    data class Call(val from: String, val to: String)
    data class Accept(val from: String, val to: String, val call_id: String)
    data class Refuse(val from: String, val to: String, val call_id: String)
}

enum class MsgType {
    LOGIN,
    CALL,
    ACCEPT,
    ACCEPTED,
    REFUSE,
    REFUSED,
    CANCEL,
    CANCELLED,
    HANGUP,
    HUNG_UP,
    OFFER,
    ANSWER,
    ICE_CANDIDATE
}
