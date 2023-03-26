package gg.beemo.latte.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
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

    suspend fun countDown() {
        val newValue = counter.decrementAndGet()
        @OptIn(ExperimentalCoroutinesApi::class)
        if (newValue != 0 || channel.isClosedForSend) {
            return
        }
        channel.send(Unit)
        channel.close()
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
