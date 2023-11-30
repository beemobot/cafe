package gg.beemo.latte.util

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onSuccess
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration

class SuspendingCountDownLatch(initialCount: Int) {

    init {
        check(initialCount > 0) { "Initial count has to be greater than zero (is $initialCount)" }
    }

    private val counter = AtomicInteger(initialCount)
    private val channel = Channel<Unit>()

    fun getCount(): Int = counter.get()

    fun countDown() {
        val newValue = counter.decrementAndGet()
        if (newValue != 0) {
            return
        }
        channel.trySend(Unit).onSuccess {
            channel.close()
        }
    }

    suspend fun await() {
        await(Duration.INFINITE)
    }

    suspend fun await(waitTime: Duration): Boolean = withTimeoutOrNull(waitTime) {
        try {
            channel.receive()
        } finally {
            channel.close()
        }
        true
    } ?: false

}
