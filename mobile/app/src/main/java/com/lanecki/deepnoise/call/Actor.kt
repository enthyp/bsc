package com.lanecki.deepnoise.call

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.lang.Exception

// Poor man's actor until some new API takes off
// TODO:
//  - exception handling
//  - queue size
open class Actor<T>(protected val dispatcher: CoroutineDispatcher) {

    private val inbox = Channel<T>(Channel.UNLIMITED)

    suspend fun send(msg: T) = withContext(dispatcher) {
        Log.d("ACTOR", "PRE INBOXED $msg ${Thread.currentThread().name}")
        inbox.send(msg)
        Log.d("ACTOR", "INBOXED $msg ${Thread.currentThread().name}")
    }

    suspend fun receive(block: suspend CoroutineScope.(T) -> Unit) = withContext(dispatcher) {
        try {
            while (true) {
                val msg = inbox.receive()
                Log.d("ACTOR", "Pre $msg ${Thread.currentThread().name}")
                block(msg)
                Log.d("ACTOR", "Post $msg ${Thread.currentThread().name}")
            }
        } catch (e: Exception) {
            Log.d("ACTOR", "Receive error $e")
        }
        //        for (msg in inbox) {
//            block(msg)
//        }
    }
}


// Protocols of different actors
sealed class Message

// CallActivity messages

class OutgoingCallMsg(val to: String) : Message()
class IncomingCallMsg(val from: String, val callId: String) : Message()
object CloseMsg : Message()

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
