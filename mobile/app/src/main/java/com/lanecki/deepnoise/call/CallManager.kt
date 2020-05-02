package com.lanecki.deepnoise.call

import android.content.Context
import android.util.Log
import com.lanecki.deepnoise.CallUI
import com.lanecki.deepnoise.call.websocket.*
import com.lanecki.deepnoise.utils.InjectionUtils
import kotlinx.coroutines.*
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.webrtc.audio.JavaAudioDeviceModule
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer


enum class CallState {
    INIT,
    INCOMING,
    OUTGOING,
    SIGNALLING,
    CLOSED
}

// TODO: some error on call to self? xd
class CallManager(
    private val nickname: String,
    private val serverAddress: String,
    private var ui: CallUI?,
    private val context: Context
) : Actor<Message>(Dispatchers.Default) {

    companion object {
        private const val TAG = "CallManager"
    }

    private val lifecycle: CallLifecycle = InjectionUtils.provideCallLifecycle()
    private val wsClient: WSClient
    private lateinit var tfliteModel: MappedByteBuffer
    private lateinit var tflite: Interpreter
    private val peerConnectionManager: PeerConnectionManager

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

        val callback = audioCallback()
        peerConnectionManager = PeerConnectionManager(this@CallManager, context, callback)
    }

    // Implement WSListener interface.
    suspend fun run() = withContext(dispatcher) {
        launch { wsClient.run() }
        peerConnectionManager.init(this)

        val response = CompletableDeferred<Unit>()
        wsClient.send(LoginMsg(nickname, response))
        response.await()

        receive { msg ->
            Log.d(TAG, "Received $msg in state $state")

            when(msg) {
                is OutgoingCallMsg -> handleOutgoingCall(msg)
                is IncomingCallMsg -> handleIncomingCall(msg)
                is HangupMsg -> handleHangup()
                is AcceptMsg -> handleAccept(msg)
                is RefuseMsg -> handleRefuse(msg)
                is AcceptedMsg -> handleAccepted(msg)
                is RefusedMsg -> handleRefused(msg)
                is OfferMsg -> handleOffer(msg, msg.out)
                is AnswerMsg -> handleAnswer(msg, msg.out)
                is IceCandidateMsg -> handleIce(msg, msg.out)
                is ConnectionClosedMsg -> handleConnectionClosed(msg)
                is CloseMsg -> handleClose()
                else -> handleOther(msg)
            }
        }
    }

    private suspend fun handleOutgoingCall(msg: OutgoingCallMsg) = withContext(dispatcher) {
        if (state == CallState.INIT) {
            state = CallState.OUTGOING
            wsClient.send(CallMsg(nickname, msg.to))
        }
    }

    private suspend fun handleIncomingCall(msg: IncomingCallMsg) = withContext(dispatcher) {
        if (state == CallState.INIT) {
            state = CallState.INCOMING
        }
    }

    private suspend fun handleHangup() = withContext(dispatcher) {
        if (state == CallState.SIGNALLING) {
            wsClient.send(HangupMsg)
            shutdown()
        }
    }

    private suspend fun handleAccept(msg: AcceptMsg) = withContext(dispatcher) {
        if (state == CallState.INCOMING) {
            state = CallState.SIGNALLING
            wsClient.send(AcceptMsg(msg.from, msg.to, msg.callId))
        }
    }

    private suspend fun handleRefuse(msg: RefuseMsg) = withContext(dispatcher) {
        if (state == CallState.INCOMING) {
            wsClient.send(RefuseMsg(msg.from, msg.to, msg.callId))
            shutdown()
        }
    }

    private suspend fun handleAccepted(msg: AcceptedMsg) = withContext(dispatcher) {
        if (state == CallState.OUTGOING) {
            state = CallState.SIGNALLING
            launch { peerConnectionManager.call() }
        }
    }

    private suspend fun handleRefused(msg: RefusedMsg) = withContext(dispatcher) {
        if (state == CallState.OUTGOING) {
            shutdown()
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

    private suspend fun handleOffer(msg: OfferMsg, out: Boolean) = withContext(dispatcher) {
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

    private suspend fun handleAnswer(msg: AnswerMsg, out: Boolean) = withContext(dispatcher) {
        if (state == CallState.SIGNALLING) {
            if (out) {
                wsClient.send(msg)
            } else {
                peerConnectionManager.onAnswerReceived(msg.sessionDescription)
            }
        } else {
            fsmError("onAnswer")
        }
    }

    private suspend fun handleIce(msg: IceCandidateMsg, out: Boolean) = withContext(dispatcher) {
        if (state == CallState.SIGNALLING) {
            if (out) {
                wsClient.send(msg)
            } else {
                peerConnectionManager.onIceCandidateReceived(msg.iceCandidate)
            }
        } else {
            fsmError("onIce")
        }
    }

    private suspend fun handleConnectionClosed(msg: ConnectionClosedMsg) = withContext(dispatcher) {
        shutdown()
        launch(Dispatchers.Main) { ui?.onCallEnd() }
    }

    private suspend fun handleClose() = withContext(dispatcher) {
        shutdown()
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

    // TODO: cancellation?
    private suspend fun shutdown() = withContext(Dispatchers.Default) {
        if (state != CallState.CLOSED) {
            state = CallState.CLOSED
            wsClient.send(CancelMsg)  // TODO: wait for it
            peerConnectionManager.close()
            Log.d(TAG, "shutdown in $state")
        }
    }
}