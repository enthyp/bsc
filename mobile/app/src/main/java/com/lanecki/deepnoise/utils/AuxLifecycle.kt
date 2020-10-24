package com.lanecki.deepnoise.utils

import com.tinder.scarlet.Lifecycle
import com.tinder.scarlet.lifecycle.LifecycleRegistry

class AuxLifecycle private constructor(private val lifecycleRegistry: LifecycleRegistry) :
    Lifecycle by lifecycleRegistry {

    constructor() : this(LifecycleRegistry())

    fun start() {
        lifecycleRegistry.onNext(Lifecycle.State.Started)
    }

    fun stop() {
        lifecycleRegistry.onNext(Lifecycle.State.Stopped.AndAborted)
    }
}