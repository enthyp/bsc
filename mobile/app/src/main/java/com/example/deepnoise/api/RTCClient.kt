package com.example.deepnoise.api

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import io.ktor.util.KtorExperimentalAPI
import org.webrtc.*


class RTCClient(remoteView: VideoSink, val context: Context) : SignalingClientListener {

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
        buildPeerConnection(remoteView)
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

    private fun buildPeerConnection(remoteView: VideoSink): PeerConnection? {
        val observer = object : PeerConnectionObserver() {
            override fun onIceCandidate(p0: IceCandidate?) {
                super.onIceCandidate(p0)
                signalingClient.send(p0)
                onIceCandidateReceived(p0!!)
            }

            override fun onAddStream(p0: MediaStream?) {
                super.onAddStream(p0)
                Log.d(TAG, "Add stream")
                p0?.videoTracks?.get(0)?.addSink(remoteView)
            }
        }

        return peerConnectionFactory.createPeerConnection(iceServer, observer)
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

        val localStream = peerConnectionFactory.createLocalMediaStream("101")
        localStream.addTrack(localVideoTrack)
        peerConnection?.addStream(localStream)
    }

    fun call() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        peerConnection?.createOffer(object : AppSdpObserver() {
            override fun onCreateSuccess(p0: SessionDescription?) {
                peerConnection?.setLocalDescription(AppSdpObserver(), p0)
                signalingClient.send(p0)
            }
        }, constraints)
    }

    fun answer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        peerConnection?.createAnswer(object : AppSdpObserver() {
            override fun onCreateSuccess(p0: SessionDescription?) {
                peerConnection?.setLocalDescription(AppSdpObserver(), p0)
                signalingClient.send(p0)
            }
        }, constraints)
    }

    override fun onIceCandidateReceived(iceCandidate: IceCandidate) {
        peerConnection?.addIceCandidate(iceCandidate)
    }

    override fun onOfferReceived(sessionDescription: SessionDescription) {
        peerConnection?.setRemoteDescription(AppSdpObserver(), sessionDescription)
        answer()
    }

    override fun onAnswerReceived(sessionDescription: SessionDescription) {
        peerConnection?.setRemoteDescription(AppSdpObserver(), sessionDescription)
    }
}
