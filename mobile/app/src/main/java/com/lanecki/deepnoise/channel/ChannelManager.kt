package com.lanecki.deepnoise.channel

import android.content.Context
import android.util.Log
import com.lanecki.deepnoise.ChannelUI
import com.lanecki.deepnoise.utils.Actor
import com.lanecki.deepnoise.utils.AuxLifecycle
import com.lanecki.deepnoise.utils.InjectionUtils
import kotlinx.coroutines.*


class ChannelManager(
    private val channelId: String,
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
        wsClient.send(JoinRequestMsg(channelId, response))
        response.await()

        receive { msg ->
            Log.d(TAG, "Received $msg in state $state")

            when(msg) {
                is AcceptedMsg -> handleAccepted(msg)

                is ReceivedOfferMsg -> handleReceivedOffer(msg)
                is ReceivedAnswerMsg -> handleReceivedAnswer(msg)
                is ReceivedIceCandidateMsg -> handleReceivedIce(msg)

                is SentOfferMsg -> handleSentOffer(msg)
                is SentAnswerMsg -> handleSentAnswer(msg)
                is SentIceCandidateMsg -> handleSentIce(msg)

                is ConnectionClosedMsg -> handleConnectionClosed(msg)
                is LeaveMsg -> handleLeave()
                else -> handleOther(msg)
            }
        }
    }

    private suspend fun handleAccepted(msg: AcceptedMsg) = withContext(dispatcher) {
        if (state == State.INIT) {
            state = State.SIGNALLING
            launch { peerConnectionManager.connect(msg.usersOnline) }
        }
    }

    private suspend fun handleReceivedOffer(msg: ReceivedOfferMsg) = withContext(dispatcher) {
        if (state == State.SIGNALLING)
            peerConnectionManager.onOfferReceived(msg.fromUser, msg.sessionDescription)
        else
            fsmError("onOffer")
    }

    private suspend fun handleReceivedAnswer(msg: ReceivedAnswerMsg) = withContext(dispatcher) {
        if (state == State.SIGNALLING)
            peerConnectionManager.onAnswerReceived(msg.fromUser, msg.sessionDescription)
        else
            fsmError("onAnswer")
    }

    private suspend fun handleReceivedIce(msg: ReceivedIceCandidateMsg) = withContext(dispatcher) {
        if (state == State.SIGNALLING)
            peerConnectionManager.onIceCandidateReceived(msg.fromUser, msg.iceCandidate)
        else
            fsmError("onIce")
    }

    private suspend fun handleSentOffer(msg: SentOfferMsg) = withContext(dispatcher) {
        if (state == State.SIGNALLING)
            wsClient.send(msg)
        else
            fsmError("onOffer")
    }

    private suspend fun handleSentAnswer(msg: SentAnswerMsg) = withContext(dispatcher) {
        if (state == State.SIGNALLING)
            wsClient.send(msg)
        else
            fsmError("onAnswer")
    }

    private suspend fun handleSentIce(msg: SentIceCandidateMsg) = withContext(dispatcher) {
        if (state == State.SIGNALLING)
            wsClient.send(msg)
        else
            fsmError("onIce")
    }
    
    private suspend fun handleConnectionClosed(msg: ConnectionClosedMsg) = withContext(dispatcher) {
        shutdown()
    }

    private suspend fun handleLeave() = withContext(dispatcher) {
        shutdown()
    }

    private suspend fun handleOther(msg: Message) = withContext(dispatcher) {
        Log.d(TAG, "Received unhandled message $msg")
        // TODO: anything else??
    }

    private fun fsmError(called: String) {
        Log.d(TAG, "Server error: $called in $state")
    }

    // TODO: cancellation?
    private suspend fun shutdown() = withContext(Dispatchers.Default) {
        if (state != State.CLOSED) {
            state = State.CLOSED
            wsClient.send(LeaveMsg)  // TODO: wait for it
            peerConnectionManager.close()
            Log.d(TAG, "shutdown in $state")
        }
    }
}