package com.lanecki.deepnoise.utils

import org.webrtc.*
import java.lang.Exception
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


// WebRTC-related listeners
interface SignallingListener {
    suspend fun onIceCandidateReceived(iceCandidate: IceCandidate)

    suspend fun onOfferReceived(sessionDescription: SessionDescription)

    suspend fun onAnswerReceived(sessionDescription: SessionDescription)
}

// WebRTC-related listeners
interface MultiplexSignallingListener {
    suspend fun onIceCandidateReceived(sender: String, iceCandidate: IceCandidate)

    suspend fun onOfferReceived(sender: String, sessionDescription: SessionDescription)

    suspend fun onAnswerReceived(sender: String, sessionDescription: SessionDescription)
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