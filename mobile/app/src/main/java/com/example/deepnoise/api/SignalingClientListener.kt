package com.example.deepnoise.api

import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

interface SignalingClientListener {
    fun onIceCandidateReceived(iceCandidate: IceCandidate)
    fun onOfferReceived(sessionDescription: SessionDescription)
    fun onAnswerReceived(sessionDescription: SessionDescription)
}