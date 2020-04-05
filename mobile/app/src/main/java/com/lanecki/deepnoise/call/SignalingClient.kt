package com.lanecki.deepnoise.call

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.GsonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.websocket.ClientWebSocketSession
import io.ktor.client.features.websocket.WebSockets
import io.ktor.client.features.websocket.ws
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.close
import io.ktor.http.cio.websocket.readText
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

@ExperimentalCoroutinesApi
@KtorExperimentalAPI
class SignalingClient(private val listener: SignalingClientListener) :
    CoroutineScope by CoroutineScope(Dispatchers.IO) {

    companion object {
        private const val HOST_ADDRESS = "192.168.100.106"
    }

    private val TAG = "SignalingClient"

    private val gson = Gson()

    private val client = HttpClient(CIO) {
        install(WebSockets)
        install(JsonFeature) {
            serializer = GsonSerializer()
        }
    }

    private val sendChannel = Channel<String>()

    private lateinit var clientSession: ClientWebSocketSession

    init {
        connect()
    }

    private fun connect() = launch {
        client.ws(host = HOST_ADDRESS, port = 5000) {
            clientSession = this

            launch {
                try {
                    while (true) {
                        sendChannel.receive().also {
                            Log.v(TAG, "Sending: $it")
                            outgoing.send(Frame.Text(it))
                        }
                    }
                } catch (exception: Throwable) {
                    Log.e(TAG,"Error...", exception)
                }
            }

            try {
                while (true) {
                    val frame = incoming.receive()
                    if (frame is Frame.Text) {
                        val data = frame.readText()
                        Log.v(TAG, "Received: $data")

                        val jsonObject = gson.fromJson(data, JsonObject::class.java)

                        launch(Dispatchers.Main) {
                            if (jsonObject.has("serverUrl")) {
                                listener.onIceCandidateReceived(gson.fromJson(jsonObject, IceCandidate::class.java))
                            } else if (jsonObject.has("type") && jsonObject.get("type").asString == "OFFER") {
                                listener.onOfferReceived(gson.fromJson(jsonObject, SessionDescription::class.java))
                            } else if (jsonObject.has("type") && jsonObject.get("type").asString == "ANSWER") {
                                listener.onAnswerReceived(gson.fromJson(jsonObject, SessionDescription::class.java))
                            }
                        }
                    }
                }
            } catch (exception: Throwable) {
                Log.e(TAG,"Error...", exception)
            }
        }


    }

    fun send(dataObject: Any?) = launch(coroutineContext) {
        sendChannel.send(gson.toJson(dataObject))
    }

    fun destroy() = launch {
        clientSession.close()
        client.close()
        cancel()
    }
}