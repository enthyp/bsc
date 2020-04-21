package com.lanecki.deepnoise.call

import com.tinder.scarlet.Lifecycle
import com.tinder.scarlet.lifecycle.LifecycleRegistry

// TODO: what's going on?
class CallLifecycle private constructor(private val lifecycleRegistry: LifecycleRegistry) :
    Lifecycle by lifecycleRegistry {

    constructor() : this(LifecycleRegistry())

    fun start() {
        lifecycleRegistry.onNext(Lifecycle.State.Started)
    }

    fun stop() {
        lifecycleRegistry.onNext(Lifecycle.State.Stopped.AndAborted)
    }
}