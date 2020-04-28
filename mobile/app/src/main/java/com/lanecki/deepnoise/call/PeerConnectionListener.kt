package com.lanecki.deepnoise.call

import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

interface PeerConnectionListener {
    fun sendOffer(sessionDescription: SessionDescription?)
    fun sendAnswer(sessionDescription: SessionDescription?)
    fun sendIceCandidate(iceCandidate: IceCandidate?)
}