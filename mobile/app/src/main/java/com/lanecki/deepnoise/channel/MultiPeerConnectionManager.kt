package com.lanecki.deepnoise.channel

import android.content.Context
import android.util.Log
import com.lanecki.deepnoise.utils.*
import kotlinx.coroutines.*
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import org.webrtc.voiceengine.WebRtcAudioUtils


// TODO: to actor?
class MultiPeerConnectionManager(
    private val listener: Actor<Message>,
    private val context: Context
) : MultiplexSignallingListener {

    enum class RTCState {
        SIGNALLING,
        CLOSED
    }

    companion object {
        private const val TAG = "PeerConnectionManager"
    }

    private var state = RTCState.SIGNALLING
    private var coroutineScope: CoroutineScope? = null

    private val iceServer = listOf(
        PeerConnection.IceServer
            .builder("stun:stun.l.google.com:19302")
            .createIceServer()
    )

    private val peerConnectionFactory: PeerConnectionFactory by lazy {
        buildPeerConnectionFactory(context)
    }

    private var peerConnections: MutableMap<String, PeerConnection?> = mutableMapOf()

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

    private suspend fun buildPeerConnection(user: String, coroutineScope: CoroutineScope): PeerConnection? {
        val observer = object : PeerConnectionObserver() {
            override fun onIceCandidate(p0: IceCandidate?) {
                super.onIceCandidate(p0)
                p0?.let { coroutineScope.launch { listener.send(
                    ReceivedIceCandidateMsg(user, p0)
                ) } }
                Log.d(TAG, "ICE candidate $p0 from PeerConnection of $user")
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

    fun init(coroutineScope: CoroutineScope) {
        this.coroutineScope = coroutineScope
    }

    private fun PeerConnection.setupAudio() {
        val audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        val localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)
        val localStream = peerConnectionFactory.createLocalMediaStream("101")
        localStream.addTrack(localAudioTrack)
        this.addStream(localStream)
    }

    // TODO: handle null pc, offers, answers...
    suspend fun connect(usersOnline: Array<String>) = withContext(Dispatchers.Default) {
        usersOnline.forEach { user ->
            val peerConnection = buildPeerConnection(user, coroutineScope!!)?.apply { this.setupAudio() }

            val offer = peerConnection?.createOfferSuspend(audioConstraints)
            peerConnection?.setLocalDescriptionSuspend(offer)
            offer?.let { listener.send(SentOfferMsg(user, offer)) }

            peerConnections[user] = peerConnection
        }

        Log.d(TAG, "Offers created.")
    }

    override suspend fun onIceCandidateReceived(sender: String, iceCandidate: IceCandidate) = withContext(Dispatchers.Default) {
        // TODO: handle missing key?
        peerConnections[sender]?.addIceCandidate(iceCandidate)
        Unit
    }

    override suspend fun onOfferReceived(sender: String, sessionDescription: SessionDescription) = withContext(Dispatchers.Default) {
        // TODO: new user -> Toast?
        if (!peerConnections.containsKey(sender))
            peerConnections[sender] = buildPeerConnection(sender, coroutineScope!!)?.apply { this.setupAudio() }

        peerConnections[sender]?.setRemoteDescriptionSuspend(sessionDescription)
        Log.d(TAG, "Remote set successfully")

        val answer = peerConnections[sender]?.createAnswerSuspend(audioConstraints)
        peerConnections[sender]?.setLocalDescriptionSuspend(answer)
        answer?.let { listener.send(
            SentAnswerMsg(sender, answer)
        ) }
        Log.d(TAG, "Answer $answer created in answer.")
        Unit
    }

    override suspend fun onAnswerReceived(sender: String, sessionDescription: SessionDescription) = withContext(Dispatchers.Default) {
        Log.d(TAG, "Answer received.")
        // TODO: handle missing key?
        peerConnections[sender]?.setRemoteDescription(AppSdpObserver(), sessionDescription)
        Unit
    }

    suspend fun close() = withContext(Dispatchers.Default) {
        // TODO: State of CallManager should be CLOSING (disable event handlers)
        if (state != RTCState.CLOSED) {
            state = RTCState.CLOSED
            //localStream.audioTracks[0].dispose() // TODO: ?
            peerConnections.forEach { (_, pc) ->
                pc?.close()
            }
        }
    }
}
