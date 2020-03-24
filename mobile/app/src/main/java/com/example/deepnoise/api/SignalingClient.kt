package com.example.deepnoise.api

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import org.json.JSONObject
import org.webrtc.*


class SignalingClient(remoteView: VideoSink, val context: Context) {

    private val TAG = "SignalingClient"

    private val requestQueue: RequestQueue by lazy {
        Volley.newRequestQueue(context.applicationContext)
    }

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
                send(p0)
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

    private fun <T> addToRequestQueue(req: Request<T>) {
        requestQueue.add(req)
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
                send(p0)
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
                send(p0)
            }
        }, constraints)
    }

    private fun send(data: Any?) {
        val jsonObj = JSONObject(gson.toJson(data))
        val url = "http://192.168.100.106:5000/echo"

        val jsonObjectRequest = JsonObjectRequest(Request.Method.POST, url, jsonObj,
            Response.Listener { response ->
                val jsonObject = gson.fromJson(response.toString(), JsonObject::class.java)

                GlobalScope.launch(Dispatchers.Main) {
                    if (jsonObject.has("serverUrl")) {
                        onIceCandidateReceived(
                            gson.fromJson(
                                jsonObject,
                                IceCandidate::class.java
                            )
                        )
                    } else if (jsonObject.has("type") && jsonObject.get("type").asString == "OFFER") {
                        onOffer(gson.fromJson(jsonObject, SessionDescription::class.java))
                    } else if (jsonObject.has("type") && jsonObject.get("type").asString == "ANSWER") {
                        onAnswer(gson.fromJson(jsonObject, SessionDescription::class.java))
                    }
                }
            },

            Response.ErrorListener { error ->
                Log.d(TAG, error.toString())
            }
        )

        addToRequestQueue(jsonObjectRequest)
    }

    private fun onIceCandidateReceived(iceCandidate: IceCandidate) {
        peerConnection?.addIceCandidate(iceCandidate)
    }

    private fun onOffer(sessionDescription: SessionDescription) {
        peerConnection?.setRemoteDescription(AppSdpObserver(), sessionDescription)
        answer()
    }

    private fun onAnswer(sessionDescription: SessionDescription) {
        peerConnection?.setRemoteDescription(AppSdpObserver(), sessionDescription)
        Log.d(TAG, "Got answer.")
    }
}
