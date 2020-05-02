package com.lanecki.deepnoise.call

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

// Poor man's actor until some new API takes off
// TODO:
//  - exception handling
//  - queue size
open class Actor<T>(protected val dispatcher: CoroutineDispatcher) {

    private val inbox = Channel<T>()

    suspend fun send(msg: T) {
        inbox.send(msg)
    }

    suspend fun receive(block: suspend CoroutineScope.(T) -> Unit) = withContext(dispatcher) {
        for (msg in inbox) {
            block(msg)
        }
    }
}


// Protocols of different actors
sealed class Message

// CallActivity messages

class OutgoingCallMsg(val to: String) : Message()
class IncomingCallMsg(val from: String, val callId: String) : Message()

// WebSocket messages

// received by WebSocket actor
class LoginMsg(val nickname: String, val response: CompletableDeferred<Unit>) : Message()
object ConnectedMsg : Message()
class CallMsg(val from: String, val to: String) : Message()
object CancelMsg: Message()
class AcceptMsg(val from: String, val to: String, val callId: String) : Message()
class RefuseMsg(val from: String, val to: String, val callId: String) : Message()
object WSClosedMsg : Message()
class ErrorMsg(val reason: String) : Message()

// sent by WebSocket actor
class AcceptedMsg(val from: String, val to: String) : Message()
class RefusedMsg(val from: String, val to: String) : Message()

// PeerConnectionManager messages
object ConnectionClosedMsg : Message()

// Common messages (WebSocket + PeerConnectionManager)
class OfferMsg(val sessionDescription: SessionDescription, val out: Boolean) : Message()
class AnswerMsg(val sessionDescription: SessionDescription, val out: Boolean) : Message()
class IceCandidateMsg(val iceCandidate: IceCandidate, val out: Boolean) : Message()

// Common messages (UI + WebSocket + PeerConnectionManager)
object HangupMsg : Message()
