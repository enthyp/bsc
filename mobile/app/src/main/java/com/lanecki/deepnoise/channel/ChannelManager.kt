package com.lanecki.deepnoise.channel

import android.content.Context
import android.util.Log
import com.lanecki.deepnoise.ChannelUI
import com.lanecki.deepnoise.utils.*
import kotlinx.coroutines.*


class ChannelManager(
    private val nickname: String,
    private val serverAddress: String,
    private var ui: ChannelUI?,
    private val context: Context
) : Actor<Message>(Dispatchers.Default) {

    companion object {
        private const val TAG = "ChannelManager"
    }

    enum class State {
        INIT,
        SIGNALLING,
        CLOSED
    }

    private val lifecycle: AuxLifecycle = InjectionUtils.provideCallLifecycle()
    private val wsClient: WebSocketChannelClient
    private val peerConnectionManager: MultiPeerConnectionManager

    private var state = State.INIT

    init {
        lifecycle.start()
        wsClient = WebSocketChannelClient(this, serverAddress, lifecycle)
        peerConnectionManager = MultiPeerConnectionManager(this@ChannelManager, context)
    }

    // Implement WSListener interface.
    suspend fun run() = withContext(dispatcher) {
        launch { wsClient.run() }
        peerConnectionManager.init(this)

        val response = CompletableDeferred<Unit>()
        wsClient.send(LoginMsg(nickname, response))
        response.await()

        receive { msg ->
            Log.d(TAG, "Received $msg in state $state")

            when(msg) {
                is OutgoingCallMsg -> handleOutgoingCall(msg)
                is IncomingCallMsg -> handleIncomingCall(msg)
                is HangupMsg -> handleHangup(msg)
                is CancelledMsg -> handleCancelled(msg)
                is HungUpMsg -> handleHungUp(msg)
                is AcceptMsg -> handleAccept(msg)
                is RefuseMsg -> handleRefuse(msg)
                is AcceptedMsg -> handleAccepted(msg)
                is RefusedMsg -> handleRefused(msg)
                is OfferMsg -> handleOffer(msg, msg.out)
                is AnswerMsg -> handleAnswer(msg, msg.out)
                is IceCandidateMsg -> handleIce(msg, msg.out)
                is ConnectionClosedMsg -> handleConnectionClosed(msg)
                is CloseMsg -> handleClose()
                else -> handleOther(msg)
            }
        }
    }

    private suspend fun handleOutgoingCall(msg: OutgoingCallMsg) = withContext(dispatcher) {
        if (state == State.INIT) {
            state = State.OUTGOING
            wsClient.send(CallMsg(nickname, msg.to))
        }
    }

    private suspend fun handleIncomingCall(msg: IncomingCallMsg) = withContext(dispatcher) {
        if (state == State.INIT) {
            state = State.INCOMING
        }
    }

    private suspend fun handleHangup(msg: HangupMsg) = withContext(dispatcher) {
        when (state) {
            State.OUTGOING -> { wsClient.send(
                CancelMsg
            ); shutdown() }
            State.SIGNALLING -> { wsClient.send(msg); shutdown() }
            else -> { /* TODO: ? */}
        }
    }

    private suspend fun handleCancelled(msg: CancelledMsg) = withContext(dispatcher) {
        if (state == State.INCOMING || state == State.SIGNALLING) {
            shutdown()
            launch(Dispatchers.Main) { ui?.onCallCancelled() }
        }
    }

    private suspend fun handleHungUp(msg: HungUpMsg) = withContext(dispatcher) {
        if (state == State.SIGNALLING) {
            wsClient.send(HangupMsg)
            shutdown()
            launch(Dispatchers.Main) { ui?.onCallHungUp(msg.from) }
        }
    }

    private suspend fun handleAccept(msg: AcceptMsg) = withContext(dispatcher) {
        if (state == State.INCOMING) {
            state =
                State.SIGNALLING
            wsClient.send(msg)
        }
    }

    private suspend fun handleRefuse(msg: RefuseMsg) = withContext(dispatcher) {
        if (state == State.INCOMING) {
            wsClient.send(msg)
            shutdown()
        }
    }

    private suspend fun handleAccepted(msg: AcceptedMsg) = withContext(dispatcher) {
        if (state == State.OUTGOING) {
            state =
                State.SIGNALLING
            launch { peerConnectionManager.call() }
        }
    }

    private suspend fun handleRefused(msg: RefusedMsg) = withContext(dispatcher) {
        if (state == State.OUTGOING) {
            shutdown()
            launch(Dispatchers.Main) { ui?.onCallRefused() }
        }
    }

    private suspend fun handleOther(msg: Message) = withContext(dispatcher) {
        Log.d(TAG, "Received unhandled message $msg")
        // TODO: anything else??
    }

    private fun onError(msgType: CallWSMessageType) {
        // TODO: probably get rid of this callback...
        fsmError("onError")
    }

    private suspend fun handleOffer(msg: OfferMsg, out: Boolean) = withContext(dispatcher) {
        if (state == State.SIGNALLING) {
            if (out) {
                wsClient.send(msg)
            } else {
                peerConnectionManager.onOfferReceived(msg.sessionDescription)
            }
        } else {
            fsmError("onOffer")
        }
    }

    private suspend fun handleAnswer(msg: AnswerMsg, out: Boolean) = withContext(dispatcher) {
        if (state == State.SIGNALLING) {
            if (out) {
                wsClient.send(msg)
            } else {
                peerConnectionManager.onAnswerReceived(msg.sessionDescription)
            }
        } else {
            fsmError("onAnswer")
        }
    }

    private suspend fun handleIce(msg: IceCandidateMsg, out: Boolean) = withContext(dispatcher) {
        if (state == State.SIGNALLING) {
            if (out) {
                wsClient.send(msg)
            } else {
                peerConnectionManager.onIceCandidateReceived(msg.iceCandidate)
            }
        } else {
            fsmError("onIce")
        }
    }

    private suspend fun handleConnectionClosed(msg: ConnectionClosedMsg) = withContext(dispatcher) {
        shutdown()
    }

    private suspend fun handleClose() = withContext(dispatcher) {
        shutdown()
    }

    private fun fsmError(called: String) {
        Log.d(TAG, "Server error: $called in $state")
    }

    // TODO: cancellation?
    private suspend fun shutdown() = withContext(Dispatchers.Default) {
        if (state != State.CLOSED) {
            state = State.CLOSED
            wsClient.send(CloseMsg)  // TODO: wait for it
            peerConnectionManager.close()
            Log.d(TAG, "shutdown in $state")
        }
    }
}