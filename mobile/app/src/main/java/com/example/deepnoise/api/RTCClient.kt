package com.example.deepnoise.api

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.google.gson.Gson
import io.ktor.util.KtorExperimentalAPI
import org.webrtc.*
import org.webrtc.voiceengine.WebRtcAudioManager
import org.webrtc.voiceengine.WebRtcAudioRecord
import org.webrtc.voiceengine.WebRtcAudioTrack
import org.webrtc.voiceengine.WebRtcAudioUtils


class RTCClient(val context: Context) : SignalingClientListener {

    private val TAG = "RTCClient"

    private val gson = Gson()

    private val iceServer = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
            .createIceServer()
    )
    private val peerConnectionFactory: PeerConnectionFactory by lazy {
        buildPeerConnectionFactory(context)
    }
    private val peerConnection: PeerConnection? by lazy {
        buildPeerConnection()
    }

    private val rootEglBase: EglBase = EglBase.create()
    private val videoCapturer by lazy { getVideoCapturer(context) }
    private val localVideoSource by lazy { peerConnectionFactory.createVideoSource(false) }

    @KtorExperimentalAPI
    private var signalingClient = SignalingClient(this)

    private fun buildPeerConnectionFactory(context: Context): PeerConnectionFactory {
        // Initialize PeerConnectionFactory options.
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        // Configure the PeerConnectionFactory builder.
        val rootEglBase: EglBase = EglBase.create()
        return PeerConnectionFactory
            .builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true))
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
                signalingClient.send(p0)
                onIceCandidateReceived(p0!!)
                Log.d(TAG, "ICE candidate from PeerConnection")
            }

            override fun onAddStream(p0: MediaStream?) {
                super.onAddStream(p0)
                Log.d(TAG, "Add stream")
//                WebRtcAudioRecord.setOnAudioSamplesReady {
//                    audioSamples: WebRtcAudioRecord.AudioSamples? ->
//                    audioSamples?.data
//                }
            }
        }

        return peerConnectionFactory.createPeerConnection(iceServer, observer)
    }

    init {
        WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true);
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))

            mandatory.add(MediaConstraints.KeyValuePair("noiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("echoCancellation", "true"))

        }
        val audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        val localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)
        val localStream = peerConnectionFactory.createLocalMediaStream("101")
        localStream.addTrack(localAudioTrack)
        peerConnection?.addStream(localStream)
    }

    private fun getVideoCapturer(context: Context) =
        Camera2Enumerator(context).run {
            deviceNames.find {
                isFrontFacing(it)
            }?.let {
                createCapturer(it, null)
            } ?: throw IllegalStateException()
        }

    fun initSurfaceView(view: SurfaceViewRenderer) = view.run {
        setMirror(true)
        setEnableHardwareScaler(true)
        init(rootEglBase.eglBaseContext, null)
    }

    fun startLocalVideoCapture(localVideoOutput: SurfaceViewRenderer) {
        val surfaceTextureHelper = SurfaceTextureHelper.create(Thread.currentThread().name, rootEglBase.eglBaseContext)
        (videoCapturer as VideoCapturer).initialize(surfaceTextureHelper, localVideoOutput.context, localVideoSource.capturerObserver)
        videoCapturer.startCapture(320, 240, 60)
        val localVideoTrack = peerConnectionFactory.createVideoTrack("100", localVideoSource)
        localVideoTrack.addSink(localVideoOutput)

        val localStream = peerConnectionFactory.createLocalMediaStream("100")
        localStream.addTrack(localVideoTrack)
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
                        signalingClient.send(p0)
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
                        signalingClient.send(p0)
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
                Log.d(TAG, "Remote creatset successfully")
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

    fun destroy() {
        signalingClient.destroy()
    }
}
