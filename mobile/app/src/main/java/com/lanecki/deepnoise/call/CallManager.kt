package com.lanecki.deepnoise.call

import android.content.Context
import android.util.Log
import com.lanecki.deepnoise.CallActivity
import com.lanecki.deepnoise.call.websocket.WSClient
import com.lanecki.deepnoise.utils.InjectionUtils
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.webrtc.audio.JavaAudioDeviceModule
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer

// TODO: this must be running in the background thread!
class CallManager(
    private val nickname: String,
    private val serverAddress: String,
    private val context: Context
) {
    private val lifecycle: CallLifecycle = InjectionUtils.provideCallLifecycle()
    private val wsClient: WSClient
    private lateinit var tfliteModel: MappedByteBuffer
    private lateinit var tflite: Interpreter

    init {
        // Initialize WebSocket connection to server.
        lifecycle.start()
        wsClient = WSClient(serverAddress, this, lifecycle)
        wsClient.receive()

        // Load speech enhancement model.
        try {
            tfliteModel = FileUtil.loadMappedFile(context, "identity_model.tflite")
            tflite = Interpreter(tfliteModel)
        } catch (e: IOException){
            // TODO: ping CallActivity to show toast and disable audioCallback.
            Log.e("tfliteSupport", "Error reading model", e)
        }
    }

    fun run(state: CallActivity.State) {
        if (state == CallActivity.State.INCOMING) {
            wsClient.send()
        } else {

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

    fun shutdown() {
        lifecycle.stop()
    }

    companion object {
        private const val TAG = "CallManager"
    }
}