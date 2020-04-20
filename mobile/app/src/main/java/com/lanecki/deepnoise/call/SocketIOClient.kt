package com.lanecki.deepnoise.call

import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter


class SocketIOClient {

    private val socket: Socket = IO.socket(HOST_ADDRESS)

    init {
        socket.on(Socket.EVENT_CONNECT, Emitter.Listener {
            socket.emit("foo", "hi")
            socket.disconnect()
        }).on("event", Emitter.Listener { }).on(Socket.EVENT_DISCONNECT,
            Emitter.Listener { })
        socket.connect()
    }

    fun send(data: Any?) {

    }

    companion object {
        private const val HOST_ADDRESS = "http://192.168.100.106:5001"  // TODO: in Settings panel
    }
}