package com.lanecki.deepnoise.call

import android.content.Context
import android.util.Log
import com.lanecki.deepnoise.CallUI
import com.lanecki.deepnoise.call.websocket.*
import com.lanecki.deepnoise.utils.InjectionUtils
import kotlinx.coroutines.*
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer


enum class CallState {
    INIT,
    INCOMING,
    RENDEZVOUS,
    SIGNALLING,
    CLOSING,
    CLOSED
}

sealed class Message

// Messages from CallActivity
sealed class CAMessage : Message()

class OutgoingCallMsg(val to: String) : CAMessage()
class IncomingCallMsg(val from: String, val callId: String) : CAMessage()

// WebSocket messages
sealed class WSMessage : Message()

class LoginMsg(val nickname: String, val response: CompletableDeferred<Unit>) : WSMessage()
object ConnectedMsg : WSMessage()
class CallMsg(val from: String, val to: String) : WSMessage()
class AcceptMsg(val from: String, val to: String, val callId: String) : WSMessage()
class RefuseMsg(val from: String, val to: String, val callId: String) : WSMessage()
class AcceptedMsg(val from: String, val to: String) : WSMessage()
class RefusedMsg(val from: String, val to: String) : WSMessage()
object WSClosedMsg : WSMessage()
class ErrorMsg(val reason: String) : WSMessage()

// PeerConnectionManager messages
sealed class PeerConnectionMessage : Message()
class ConnectionClosedMsg(reason: String) : PeerConnectionMessage()

// Common messages
class OfferMsg(val sessionDescription: SessionDescription, val out: Boolean) : WSMessage()
class AnswerMsg(val sessionDescription: SessionDescription, val out: Boolean) : WSMessage()
class IceCandidateMsg(val iceCandidate: IceCandidate, val out: Boolean) : WSMessage()


class CallManager(
    private val nickname: String,
    private val serverAddress: String,
    private var ui: CallUI?,
    private val context: Context
) : Actor<Message>(Dispatchers.Default) {

    private val lifecycle: CallLifecycle = InjectionUtils.provideCallLifecycle()
    private val wsClient: WSClient
    private lateinit var tfliteModel: MappedByteBuffer
    private lateinit var tflite: Interpreter
    private lateinit var peerConnectionManager: PeerConnectionManager

    private var state = CallState.INIT

    init {
        lifecycle.start()
        wsClient = WSClient(this, serverAddress, lifecycle)

        try {
            tfliteModel = FileUtil.loadMappedFile(context, "identity_model.tflite")
            tflite = Interpreter(tfliteModel)
        } catch (e: IOException){
            ui?.onModelLoadFailure()
        }
    }

    // Implement WSListener interface.
    suspend fun run() = withContext(dispatcher) {
        launch { wsClient.run() }

        val response = CompletableDeferred<Unit>()
        wsClient.send(LoginMsg(nickname, response))
        response.await()

        receive { msg ->
            when(msg) {
                is OutgoingCallMsg -> handleOutgoingCall(msg)
                is IncomingCallMsg -> handleIncomingCall(msg)
                is AcceptMsg -> handleAccept(msg)
                is RefuseMsg -> handleRefuse(msg)
                is AcceptedMsg -> handleAccepted(msg)
                is RefusedMsg -> handleRefused(msg)
                is OfferMsg -> handleOffer(msg, msg.out)
                is AnswerMsg -> handleAnswer(msg, msg.out)
                is IceCandidateMsg -> handleIce(msg, msg.out)
                else -> handleOther(msg)
            }
//            if (state == CallState.OUTGOING) {
//                state = CallState.RENDEZVOUS
//                launch { wsClient.send(MsgType.CALL, CallMsg(nickname, callee)) }
//                Log.d(TAG, "send CALL in $state")
//            } else {
//                val callback = audioCallback()
//                peerConnectionManager = PeerConnectionManager(this@CallManager, context, callback)
//                state = CallState.SIGNALLING
//                launch { wsClient.send(MsgType.ACCEPT, AcceptMsg(nickname, callee, callId!!)) }
//                Log.d(TAG, "send ACCEPT in $state")
//            }
        }
    }

    private suspend fun handleOutgoingCall(msg: OutgoingCallMsg) = withContext(dispatcher) {
        if (state == CallState.INIT) {
            state = CallState.RENDEZVOUS
            wsClient.send(CallMsg(nickname, msg.to))
        }
    }

    private suspend fun handleIncomingCall(msg: IncomingCallMsg) = withContext(dispatcher) {
        if (state == CallState.INIT) {
            state = CallState.INCOMING
        }
    }

    private suspend fun handleAccept(msg: AcceptMsg) = withContext(dispatcher) {
        if (state == CallState.INCOMING) {
            state = CallState.SIGNALLING
            val callback = audioCallback()
            peerConnectionManager = PeerConnectionManager(this@CallManager, context, callback)
            wsClient.send(AcceptMsg(nickname, msg.from, msg.callId))
        }
    }

    private suspend fun handleRefuse(msg: RefuseMsg) = withContext(dispatcher) {
        if (state == CallState.INCOMING) {
            state = CallState.CLOSING
            wsClient.send(RefuseMsg(msg.from, msg.to, msg.callId))
            // TODO: close! send msg?
        }
    }

    private suspend fun handleAccepted(msg: AcceptedMsg) = withContext(dispatcher) {
        if (state == CallState.RENDEZVOUS) {
            state = CallState.SIGNALLING
            val callback = audioCallback()
            peerConnectionManager = PeerConnectionManager(this@CallManager, context, callback)
            launch { peerConnectionManager.run() }
        }
    }

    private suspend fun handleRefused(msg: RefusedMsg) = withContext(dispatcher) {
        if (state == CallState.RENDEZVOUS) {
            state = CallState.CLOSING
            // TODO: close! send msg?
        }
    }

    private suspend fun handleOther(msg: Message) = withContext(dispatcher) {
        Log.d(TAG, "Received unhandled message $msg")
        // TODO: anything else??
    }

    private fun onError(msgType: MsgType) {
        // TODO: probably get rid of this callback...
        fsmError("onError")
    }

    private suspend fun handleOffer(msg: OfferMsg, out: Boolean) {
        Log.d(TAG, "onOffer in $state")

        if (state == CallState.SIGNALLING) {
            if (out) {
                wsClient.send(msg)
            } else {
                peerConnectionManager.onOfferReceived(msg.sessionDescription)
            }
        } else {
            fsmError("onOffer")
        }
    }

    private suspend fun handleAnswer(msg: AnswerMsg, out: Boolean) {
        Log.d(TAG, "onAnswer in $state")

        if (state == CallState.SIGNALLING) {
            if (out) {
                wsClient.send(msg)
            } else {
                peerConnectionManager.onAnswerReceived(msg.sessionDescription)
            }
        } else {
            fsmError("onOffer")
        }
    }

    private suspend fun handleIce(msg: IceCandidateMsg, out: Boolean) {
        Log.d(TAG, "onIce in $state")

        if (state == CallState.SIGNALLING) {
            if (out) {
                wsClient.send(msg)
            } else {
                peerConnectionManager.onIceCandidateReceived(msg.iceCandidate)
            }
        } else {
            fsmError("onOffer")
        }
    }

    private fun audioCallback() = object : JavaAudioDeviceModule.AudioTrackProcessingCallback {
        // TODO: should be taken from some configuration (changeable maybe?)
        private val BUFFER_SIZE = 441  // number of samples
        private var inBuffer: FloatArray = FloatArray(BUFFER_SIZE)
        private var outBuffer: Array<FloatArray> = arrayOf(FloatArray(BUFFER_SIZE))

        override fun onWebRtcAudioTrackProcess(p0: ByteBuffer?) {
            if (p0 == null) return;

            for (i in 0 until BUFFER_SIZE)
                inBuffer[i] = p0.getShort(2 * i).toFloat()

            tflite.run(inBuffer, outBuffer)

            for (i in 0 until BUFFER_SIZE)
                p0.putShort(2 * i, outBuffer[0][i].toShort())
        }
    }

    private fun fsmError(called: String) {
        Log.d(TAG, "Server error: $called in $state")
    }

    fun shutdown() {
        lifecycle.stop()
        state = CallState.CLOSED
        Log.d(TAG, "shutdown in $state")
        // TODO: send poison pills?
    }

    companion object {
        private const val TAG = "CallManager"
    }
}