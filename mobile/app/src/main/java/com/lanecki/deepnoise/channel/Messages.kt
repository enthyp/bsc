package com.lanecki.deepnoise.channel

import kotlinx.coroutines.CompletableDeferred
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription


// Protocols of channel management actors
sealed class Message

// ChannelActivity
class JoinMsg(val channelId: String) : Message()

// WebSocket messages

// received by WebSocket actor from the server
class AcceptedMsg(val usersOnline: Array<String>) : Message()
object ConnectedMsg : Message()
object WSClosedMsg : Message()
class ErrorMsg(val reason: String) : Message()

// received by WebSocket actor from the application actors
class JoinRequestMsg(val channelId: String, val response: CompletableDeferred<Unit>) : Message()

// Common messages (WebSocket + PeerConnectionManager)
class ReceivedOfferMsg(val fromUser: String, val sessionDescription: SessionDescription) : Message()
class ReceivedAnswerMsg(val fromUser: String, val sessionDescription: SessionDescription) : Message()
class ReceivedIceCandidateMsg(val fromUser: String, val iceCandidate: IceCandidate) : Message()

class SentOfferMsg(val toUser: String, val sessionDescription: SessionDescription) : Message()
class SentAnswerMsg(val toUser: String, val sessionDescription: SessionDescription) : Message()
class SentIceCandidateMsg(val toUser: String ,val iceCandidate: IceCandidate) : Message()

// Common messages (UI + WebSocket + PeerConnectionManager)
object LeaveMsg : Message()



// PeerConnectionManager messages
object ConnectionClosedMsg : Message()
