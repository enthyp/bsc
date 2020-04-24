package com.lanecki.deepnoise.call.websocket

import com.tinder.scarlet.WebSocket
import com.tinder.scarlet.ws.Receive
import com.tinder.scarlet.ws.Send
import kotlinx.coroutines.channels.ReceiveChannel

interface WSApi {
    @Send
    fun sendSignal(data: Any)

    @Receive
    fun receiveSignal(): ReceiveChannel<Any>

    @Receive
    fun receiveWebSocketEvent(): ReceiveChannel<WebSocket.Event>  // TODO: use it...
}