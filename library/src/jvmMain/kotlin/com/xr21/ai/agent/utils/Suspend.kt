package com.xr21.ai.agent.utils

import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine


@SinceKotlin("1.3")
fun <T> runSuspend(block: suspend () -> T): T {
    val run = RunSuspend<T>()
    block.startCoroutine(run)
    return run.await()
}

class RunSuspend<T>(override val context: CoroutineContext = EmptyCoroutineContext) : Continuation<T> {
    var result: Result<T>? = null

    override fun resumeWith(result: Result<T>) = synchronized(this) {
        this.result = result
        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") (this as Object).notifyAll()
    }

    fun await(): T {
        synchronized(this) {
            while (true) {
                val result = this.result
                if (result == null) {
                    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                    (this as Object).wait()
                } else {
                    return result.getOrThrow()
                }
            }
        }
    }
}
