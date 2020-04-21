package com.lanecki.deepnoise.call

import android.content.Context
import android.util.Log
import com.lanecki.deepnoise.utils.InjectionUtils
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import org.webrtc.voiceengine.WebRtcAudioUtils

// TODO: this must be running in the background thread!
class CallHandler(
    private val nickname: String,
    private val serverAddress: String,
    private val audioSamplesCallback: JavaAudioDeviceModule.AudioTrackProcessingCallback,
    private val context: Context
    ) : SignallingListener {

    private val lifecycle: CallLifecycle = InjectionUtils.provideCallLifecycle()
    private val wsClient: WSClient

    init {
        lifecycle.start()
        wsClient = WSClient(serverAddress, this, lifecycle)
    }

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
                p0?.let { wsClient.send(WSClient.ICE_CANDIDATE, p0) }
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

        wsClient.receive()
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
                        p0?.let {wsClient.send(WSClient.OFFER, p0) }
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
                        p0?.let { wsClient.send(WSClient.ANSWER, p0) }
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

    fun shutdown() {
        lifecycle.stop()
    }

    companion object {
        private const val TAG = "CallHandler"
    }
}