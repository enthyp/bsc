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
    INCOMING,
    OUTGOING,
    AWAITING_RESPONSE,
    SIGNALLING,
    CLOSING
}

// TODO: this must be running in the background thread!
class CallManager(
    private var state: CallState,
    private val nickname: String,
    private val callee: String,
    private var callId: String?,
    private val serverAddress: String,
    private val ui: CallUI,
    private val context: Context
) : WSListener,
    PeerConnectionListener,
    CoroutineScope by CoroutineScope(Dispatchers.Default) {

    private val lifecycle: CallLifecycle = InjectionUtils.provideCallLifecycle()
    private val wsClient: WSClient
    private lateinit var tfliteModel: MappedByteBuffer
    private lateinit var tflite: Interpreter
    private lateinit var peerConnectionManager: PeerConnectionManager  // TODO: this sucks

    init {
        lifecycle.start()
        wsClient = WSClient(serverAddress, lifecycle)

        try {
            tfliteModel = FileUtil.loadMappedFile(context, "identity_model.tflite")
            tflite = Interpreter(tfliteModel)
        } catch (e: IOException){
            ui.onModelLoadFailure()
        }
    }

    // Implement WSListener interface.
    suspend fun run() = withContext(this.coroutineContext) {
        launch { wsClient.receive(this@CallManager) }
        wsClient.sendNickname(nickname)

        if (state == CallState.OUTGOING) {
            state = CallState.AWAITING_RESPONSE
            launch { wsClient.send(MsgType.CALL, CallMsg(nickname, callee)) }
            Log.d(TAG, "send CALL in $state")
        } else {
            val callback = audioCallback()
            peerConnectionManager = PeerConnectionManager(this@CallManager, context, callback)
            state = CallState.SIGNALLING
            launch { wsClient.send(MsgType.ACCEPT, AcceptMsg(nickname, callee, callId!!)) }
            Log.d(TAG, "send ACCEPT in $state")
        }
    }

    override fun onAccepted(msg: AcceptedMsg) {
        Log.d(TAG, "onAccepted in $state")

        if (state == CallState.AWAITING_RESPONSE) {
            val callback = audioCallback()
            peerConnectionManager = PeerConnectionManager(this@CallManager, context, callback)
            state = CallState.SIGNALLING
            launch { peerConnectionManager.call() }
        } else {
            fsmError("onAccepted")
        }

        return
    }

    override fun onRefused(msg: RefusedMsg) {
        Log.d(TAG, "onRefused in $state")

        if (state == CallState.AWAITING_RESPONSE) {
            // TODO: gracefully
            ui.onCallRefused()
            state = CallState.CLOSING
            shutdown()
        } else {
            fsmError("onRefused")
        }
    }

    override fun onError(msgType: MsgType) {
        // TODO: probably get rid of this callback...
        fsmError("onError")
    }

    override suspend fun onOffer(sessionDescription: SessionDescription) {
        Log.d(TAG, "onOffer in $state")

        if (state == CallState.SIGNALLING) {
            peerConnectionManager.onOfferReceived(sessionDescription)
        } else {
            fsmError("onOffer")
        }
    }

    override suspend fun onAnswer(sessionDescription: SessionDescription) {
        Log.d(TAG, "onAnswer in $state")

        if (state == CallState.SIGNALLING) {
            peerConnectionManager.onAnswerReceived(sessionDescription)
        } else {
            fsmError("onAnswer")
        }
    }

    override suspend fun onIceCandidate(iceCandidate: IceCandidate) {
        Log.d(TAG, "onIce in $state")

        if (state == CallState.SIGNALLING) {
            peerConnectionManager.onIceCandidateReceived(iceCandidate)
        } else {
            fsmError("onIceCandidate")
        }
    }

    // Implement PeerConnectionListener interface.
    override fun sendOffer(sessionDescription: SessionDescription?) {
        Log.d(TAG, "sendOffer in $state")

        launch { wsClient.send(MsgType.OFFER, sessionDescription) }
    }

    override fun sendAnswer(sessionDescription: SessionDescription?) {
        Log.d(TAG, "sendAnswer in $state")

        launch { wsClient.send(MsgType.ANSWER, sessionDescription) }
    }

    override fun sendIceCandidate(iceCandidate: IceCandidate?) {
        Log.d(TAG, "sendIce in $state")

        launch { wsClient.send(MsgType.ICE_CANDIDATE, iceCandidate) }
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
        peerConnectionManager.shutdown()
        lifecycle.stop()
        Log.d(TAG, "shutdown in $state")
    }

    companion object {
        private const val TAG = "CallManager"
    }
}