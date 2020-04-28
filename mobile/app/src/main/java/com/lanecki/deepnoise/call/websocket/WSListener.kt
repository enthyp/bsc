package com.lanecki.deepnoise.call.websocket

import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

interface WSListener {
    suspend fun onOffer(sessionDescription: SessionDescription)
    suspend fun onAnswer(sessionDescription: SessionDescription)
    suspend fun onIceCandidate(iceCandidate: IceCandidate)
    fun onAccepted(msg: AcceptedMsg)
    fun onRefused(msg: RefusedMsg)
    fun onError(msgType: MsgType)
}