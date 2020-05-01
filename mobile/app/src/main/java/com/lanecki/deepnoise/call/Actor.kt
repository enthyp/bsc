package com.lanecki.deepnoise.call

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext

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