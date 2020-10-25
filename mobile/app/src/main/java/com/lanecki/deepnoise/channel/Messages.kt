package com.lanecki.deepnoise.channel

import kotlinx.coroutines.CompletableDeferred
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription


// Protocols of channel management actors
sealed class Message

// ChannelActivity

class JoinMsg(val channelId: String) : Message()
object LeaveMsg : Message()

// WebSocket messages

// received by WebSocket actor
class LoginMsg(val nickname: String, val response: CompletableDeferred<Unit>) : Message()
object ConnectedMsg : Message()
class CallMsg(val from: String, val to: String) : Message()
class AcceptMsg(val from: String, val to: String, val callId: String) : Message()
class RefuseMsg(val from: String, val to: String, val callId: String) : Message()
object WSClosedMsg : Message()
class ErrorMsg(val reason: String) : Message()

// sent by WebSocket actor
class AcceptedMsg(val from: String, val to: String) : Message()
class RefusedMsg(val from: String, val to: String) : Message()
object CancelledMsg : Message()
class HungUpMsg(val from: String, callId: String) : Message()

// PeerConnectionManager messages
object ConnectionClosedMsg : Message()

// Common messages (WebSocket + PeerConnectionManager)
class OfferMsg(val sessionDescription: SessionDescription, val out: Boolean) : Message()
class AnswerMsg(val sessionDescription: SessionDescription, val out: Boolean) : Message()
class IceCandidateMsg(val iceCandidate: IceCandidate, val out: Boolean) : Message()

// Common messages (UI + WebSocket + PeerConnectionManager)
object HangupMsg : Message()
object CancelMsg : Message()
