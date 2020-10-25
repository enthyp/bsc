package com.lanecki.deepnoise.channel

import android.content.Context
import android.util.Log
import com.lanecki.deepnoise.call.*
import com.lanecki.deepnoise.utils.*
import kotlinx.coroutines.*
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import org.webrtc.voiceengine.WebRtcAudioUtils


// TODO: to actor?
class MultiPeerConnectionManager(
    private val listener: Actor<Message>,
    private val context: Context
) : SignallingListener {

    enum class RTCState {
        SIGNALLING,
        CLOSED
    }

    companion object {
        private const val TAG = "PeerConnectionManager"
    }

    private var state =
        RTCState.SIGNALLING

    private val iceServer = listOf(
        PeerConnection.IceServer
            .builder("stun:stun.l.google.com:19302")
            .createIceServer()
    )

    private val peerConnectionFactory: PeerConnectionFactory by lazy {
        buildPeerConnectionFactory(context)
    }

    private var peerConnection: PeerConnection? = null

    private fun buildPeerConnectionFactory(context: Context): PeerConnectionFactory {
        // Initialize PeerConnectionFactory options.
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        // Configure the PeerConnectionFactory builder.
        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .createAudioDeviceModule()

        return PeerConnectionFactory
            .builder()
            .setAudioDeviceModule(audioDeviceModule)
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = true
                disableNetworkMonitor = true
            })
            .createPeerConnectionFactory()
    }

    private suspend fun buildPeerConnection(coroutineScope: CoroutineScope): PeerConnection? {
        val observer = object : PeerConnectionObserver() {
            override fun onIceCandidate(p0: IceCandidate?) {
                super.onIceCandidate(p0)
                p0?.let { coroutineScope.launch { listener.send(
                    IceCandidateMsg(
                        p0,
                        true
                    )
                ) } }
                Log.d(TAG, "ICE candidate $p0 from PeerConnection")
            }

            override fun onAddStream(p0: MediaStream?) {
                super.onAddStream(p0)
                p0?.audioTracks?.get(0)?.setEnabled(true)
                Log.d(TAG, "Add stream")
            }

            override fun onRenegotiationNeeded() {
                super.onRenegotiationNeeded()
                // TODO: this rolls us back to offer exchange
                Log.d(TAG, "Renegotiation needed!")
            }

            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
                super.onIceConnectionChange(p0)
                when (p0) {
                    PeerConnection.IceConnectionState.DISCONNECTED,
                        PeerConnection.IceConnectionState.CLOSED,
                        PeerConnection.IceConnectionState.FAILED -> {
                        Log.d(TAG, "Connection closed: $p0")
                        coroutineScope.launch { listener.send(ConnectionClosedMsg) }
                    }
                    else -> {}
                }
            }
        }

        return peerConnectionFactory.createPeerConnection(iceServer, observer)
    }

    private val audioConstraints: MediaConstraints

    init {
        WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true)
        WebRtcAudioUtils.setWebRtcBasedNoiseSuppressor(true)
        WebRtcAudioUtils.setWebRtcBasedAutomaticGainControl(true)
        WebRtcAudioUtils.setDefaultSampleRateHz(16000)
        audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
    }

    suspend fun init(coroutineScope: CoroutineScope) {
        peerConnection = buildPeerConnection(coroutineScope)
        val audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        val localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)
        val localStream = peerConnectionFactory.createLocalMediaStream("101")
        localStream.addTrack(localAudioTrack)
        peerConnection?.addStream(localStream)
    }

    // TODO: handle null pc, offers, answers...
    suspend fun call() = withContext(Dispatchers.Default) {
        val offer = peerConnection?.createOfferSuspend(audioConstraints)
        peerConnection?.setLocalDescriptionSuspend(offer)
        offer?.let { listener.send(
            OfferMsg(
                offer,
                true
            )
        ) }

        Log.d(TAG, "Offer created in call.")
    }

    override suspend fun onIceCandidateReceived(iceCandidate: IceCandidate) = withContext(Dispatchers.Default) {
        peerConnection?.addIceCandidate(iceCandidate)
        Unit
    }

    override suspend fun onOfferReceived(sessionDescription: SessionDescription) = withContext(Dispatchers.Default) {
        peerConnection?.setRemoteDescriptionSuspend(sessionDescription)
        Log.d(TAG, "Remote set successfully")

        val answer = peerConnection?.createAnswerSuspend(audioConstraints)
        peerConnection?.setLocalDescriptionSuspend(answer)
        answer?.let { listener.send(
            AnswerMsg(
                answer,
                true
            )
        ) }
        Log.d(TAG, "Answer $answer created in answer.")
        Unit
    }

    override suspend fun onAnswerReceived(sessionDescription: SessionDescription) = withContext(Dispatchers.Default) {
        Log.d(TAG, "Answer received.")
        peerConnection?.setRemoteDescription(AppSdpObserver(), sessionDescription)
        Unit
    }

    suspend fun close() = withContext(Dispatchers.Default) {
        // TODO: State of CallManager should be CLOSING (disable event handlers)
        if (state != RTCState.CLOSED) {
            state =
                RTCState.CLOSED
            //localStream.audioTracks[0].dispose() // TODO: ?
            peerConnection?.close()
        }
    }
}
