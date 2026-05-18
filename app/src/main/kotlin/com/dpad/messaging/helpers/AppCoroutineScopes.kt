package com.dpad.messaging.helpers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object AppCoroutineScopes {
    private val rootJob = SupervisorJob()
    val io: CoroutineScope = CoroutineScope(rootJob + Dispatchers.IO)
    val main: CoroutineScope = CoroutineScope(rootJob + Dispatchers.Main.immediate)

    fun cancelAll() {
        rootJob.cancel()
    }
}
