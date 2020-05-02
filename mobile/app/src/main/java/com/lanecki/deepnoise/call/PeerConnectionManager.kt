package com.lanecki.deepnoise.call

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import org.webrtc.voiceengine.WebRtcAudioUtils
import java.lang.Exception
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

enum class RTCState {
    SIGNALLING,
    CLOSED
}

// TODO: to actor?
class PeerConnectionManager(
    private val listener: Actor<Message>,
    private val context: Context,
    private val audioSamplesCallback: JavaAudioDeviceModule.AudioTrackProcessingCallback
) : SignallingListener {

    companion object {
        private const val TAG = "PeerConnectionManager"
    }

    private var state = RTCState.SIGNALLING

    private val iceServer = listOf(
        PeerConnection.IceServer
            .builder("stun:stun.l.google.com:19302")
            .createIceServer()
    )

    private val peerConnectionFactory: PeerConnectionFactory by lazy {
        buildPeerConnectionFactory(context)
    }

    private val peerConnection: PeerConnection? by lazy {
        buildPeerConnection()
    }

    private fun buildPeerConnectionFactory(context: Context): PeerConnectionFactory {
        // Initialize PeerConnectionFactory options.
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        // Configure the PeerConnectionFactory builder.
        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setAudioTrackProcessingCallback(audioSamplesCallback)
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

    private fun buildPeerConnection(): PeerConnection? = runBlocking {
        val observer = object : PeerConnectionObserver() {
            override fun onIceCandidate(p0: IceCandidate?) {
                super.onIceCandidate(p0)
                p0?.let { launch { listener.send(IceCandidateMsg(p0, true)) } }
                Log.d(TAG, "ICE candidate from PeerConnection")
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
                        launch { listener.send(ConnectionClosedMsg) }
                    }
                    else -> {}
                }
            }
        }

        return@runBlocking peerConnectionFactory.createPeerConnection(iceServer, observer)
    }

    private val localStream: MediaStream

    init {
        WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true)
        WebRtcAudioUtils.setWebRtcBasedNoiseSuppressor(true)
        WebRtcAudioUtils.setWebRtcBasedAutomaticGainControl(true)

        val audioConstraints = MediaConstraints()
        val audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        val localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)
        localStream = peerConnectionFactory.createLocalMediaStream("101")
        localStream.addTrack(localAudioTrack)
        peerConnection?.addStream(localStream)
    }

    suspend fun run() = withContext(Dispatchers.Default) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        val offer = peerConnection?.createOfferSuspend(constraints)
        peerConnection?.setLocalDescriptionSuspend(offer)
        offer?.let { listener.send(OfferMsg(offer, true)) }
        Log.d(TAG, "Offer created in call.")
    }

    private suspend fun answer() = withContext(Dispatchers.Default) {
        Log.d(TAG, "ANSWER CALLED")
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        val answer = peerConnection?.createAnswerSuspend(constraints)
        peerConnection?.setLocalDescriptionSuspend(answer)
        answer?.let { listener.send(AnswerMsg(answer, true)) }
        Log.d(TAG, "Answer created in answer.")
    }

    override suspend fun onIceCandidateReceived(iceCandidate: IceCandidate) = withContext(Dispatchers.Default) {
        peerConnection?.addIceCandidate(iceCandidate)
        Unit
    }

    override suspend fun onOfferReceived(sessionDescription: SessionDescription) = withContext(Dispatchers.Default) {
        Log.d(TAG, "Offer received.")
        peerConnection?.setRemoteDescriptionSuspend(sessionDescription)
        Log.d(TAG, "Remote set successfully")
        answer()
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
            state = RTCState.CLOSED
            //localStream.audioTracks[0].dispose() // TODO: ?
            peerConnection?.close()
        }
    }
}

// WebRTC-related listeners
interface SignallingListener {
    suspend fun onIceCandidateReceived(iceCandidate: IceCandidate)

    suspend fun onOfferReceived(sessionDescription: SessionDescription)

    suspend fun onAnswerReceived(sessionDescription: SessionDescription)
}

open class PeerConnectionObserver : PeerConnection.Observer {
    override fun onIceCandidate(p0: IceCandidate?) {}

    override fun onDataChannel(p0: DataChannel?) {}

    override fun onIceConnectionReceivingChange(p0: Boolean) {}

    override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}

    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}

    override fun onAddStream(p0: MediaStream?) {}

    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}

    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}

    override fun onRemoveStream(p0: MediaStream?) {}

    override fun onRenegotiationNeeded() {}

    override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
}

open class AppSdpObserver : SdpObserver {
    override fun onSetFailure(p0: String?) {}

    override fun onSetSuccess() {}

    override fun onCreateSuccess(p0: SessionDescription?) {}

    override fun onCreateFailure(p0: String?) {}
}

// Coroutine extensions for PeerConnection
class WebRTCException(reason: String?) : Exception(reason)

suspend fun PeerConnection.createOfferSuspend(constraints: MediaConstraints) =
    suspendCoroutine<SessionDescription?> { cont ->
        createOffer(object : AppSdpObserver() {
            override fun onCreateSuccess(p0: SessionDescription?) {
                super.onCreateSuccess(p0)
                cont.resume(p0)
            }
            override fun onCreateFailure(p0: String?) {
                super.onCreateFailure(p0)
                cont.resumeWithException(WebRTCException(p0))
            }
        }, constraints)
    }

suspend fun PeerConnection.createAnswerSuspend(constraints: MediaConstraints) =
    suspendCoroutine<SessionDescription?> { cont ->
        createAnswer(object : AppSdpObserver() {
            override fun onCreateSuccess(p0: SessionDescription?) {
                super.onCreateSuccess(p0)
                cont.resume(p0)
            }
            override fun onCreateFailure(p0: String?) {
                super.onCreateFailure(p0)
                cont.resumeWithException(WebRTCException(p0))
            }
        }, constraints)
    }

suspend fun PeerConnection.setLocalDescriptionSuspend(sdp: SessionDescription?) =
    suspendCoroutine<Unit> { cont ->
        setLocalDescription(object : AppSdpObserver() {
            override fun onSetSuccess() {
                super.onSetSuccess()
                cont.resume(Unit)
            }
            override fun onSetFailure(p0: String?) {
                super.onSetFailure(p0)
                cont.resumeWithException(WebRTCException(p0))
            }
        }, sdp)
    }

suspend fun PeerConnection.setRemoteDescriptionSuspend(sdp: SessionDescription?) =
    suspendCoroutine<Unit> { cont ->
        setRemoteDescription(object : AppSdpObserver() {
            override fun onSetSuccess() {
                super.onSetSuccess()
                cont.resume(Unit)
            }
            override fun onSetFailure(p0: String?) {
                super.onSetFailure(p0)
                cont.resumeWithException(WebRTCException(p0))
            }
        }, sdp)
    }