package com.lanecki.deepnoise.api

import com.tinder.scarlet.WebSocket
import com.tinder.scarlet.ws.Receive
import com.tinder.scarlet.ws.Send
import kotlinx.coroutines.channels.ReceiveChannel

interface WSApi {
    @Send
    fun sendSignal(data: String)

    @Receive
    fun receiveSignal(): ReceiveChannel<String>

    @Receive
    fun receiveWebSocketEvent(): ReceiveChannel<WebSocket.Event>
}