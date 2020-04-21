package com.lanecki.deepnoise.call

import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

interface SignallingListener {
    fun onIceCandidateReceived(iceCandidate: IceCandidate)
    fun onOfferReceived(sessionDescription: SessionDescription)
    fun onAnswerReceived(sessionDescription: SessionDescription)
}