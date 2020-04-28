package com.lanecki.deepnoise.call

import android.content.Context
import android.util.Log
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import org.webrtc.voiceengine.WebRtcAudioUtils

class PeerConnectionManager(
    private val listener: PeerConnectionListener,
    private val context: Context,
    private val audioSamplesCallback: JavaAudioDeviceModule.AudioTrackProcessingCallback
) : SignallingListener {

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

    private fun buildPeerConnection(): PeerConnection? {
        val observer = object : PeerConnectionObserver() {
            override fun onIceCandidate(p0: IceCandidate?) {
                super.onIceCandidate(p0)
                p0?.let { listener.sendIceCandidate(p0) }
                onIceCandidateReceived(p0!!)
                Log.d(TAG, "ICE candidate from PeerConnection")
            }

            override fun onAddStream(p0: MediaStream?) {
                super.onAddStream(p0)
                p0?.audioTracks?.get(0)?.setEnabled(true)
                Log.d(TAG, "Add stream")
            }
        }

        return peerConnectionFactory.createPeerConnection(iceServer, observer)
    }

    init {
        WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true)
        WebRtcAudioUtils.setWebRtcBasedNoiseSuppressor(true)
        WebRtcAudioUtils.setWebRtcBasedAutomaticGainControl(true)

        val audioConstraints = MediaConstraints()
        val audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        val localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)
        val localStream = peerConnectionFactory.createLocalMediaStream("101")
        localStream.addTrack(localAudioTrack)
        peerConnection?.addStream(localStream)
    }

    fun call() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        peerConnection?.createOffer(object : AppSdpObserver() {
            override fun onCreateSuccess(p0: SessionDescription?) {
                peerConnection?.setLocalDescription(object : AppSdpObserver() {
                    override fun onSetSuccess() {
                        super.onSetSuccess()
                        p0?.let { listener.sendOffer(p0) }
                        Log.d(TAG, "Offer created in call.")
                    }
                }, p0)
            }
        }, constraints)
    }

    fun answer() {
        Log.d(TAG, "ANSWER CALLED")
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        peerConnection?.createAnswer(object : AppSdpObserver() {
            override fun onCreateSuccess(p0: SessionDescription?) {
                peerConnection?.setLocalDescription(object : AppSdpObserver() {
                    override fun onSetSuccess() {
                        super.onSetSuccess()
                        p0?.let { listener.sendAnswer(p0) }
                        Log.d(TAG, "Answer created in answer.")
                    }
                }, p0)
            }
        }, constraints)
    }

    override fun onIceCandidateReceived(iceCandidate: IceCandidate) {
        peerConnection?.addIceCandidate(iceCandidate)
    }

    override fun onOfferReceived(sessionDescription: SessionDescription) {
        Log.d(TAG, "Offer received.")
        peerConnection!!.setRemoteDescription(object : AppSdpObserver() {
            override fun onCreateFailure(p0: String?) {
                super.onCreateFailure(p0)
                Log.d(TAG, "FUCK CREATE")
            }
            override fun onCreateSuccess(p0: SessionDescription?) {
                super.onCreateSuccess(p0)
                Log.d(TAG, "Remote created successfully")
                answer()
            }
            override fun onSetSuccess() {
                super.onSetSuccess()
                Log.d(TAG, "Remote set successfully")
                answer()
            }

            override fun onSetFailure(p0: String?) {
                super.onSetFailure(p0)
                Log.d(TAG, p0!!)
            }
        }, sessionDescription)
    }

    override fun onAnswerReceived(sessionDescription: SessionDescription) {
        Log.d(TAG, "Answer received.")
        peerConnection?.setRemoteDescription(AppSdpObserver(), sessionDescription)
    }

    companion object {
        private const val TAG = "PeerConnectionManager"
    }
}

// WebRTC-related listeners.

interface SignallingListener {
    fun onIceCandidateReceived(iceCandidate: IceCandidate)

    fun onOfferReceived(sessionDescription: SessionDescription)

    fun onAnswerReceived(sessionDescription: SessionDescription)
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
