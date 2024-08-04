package gg.beemo.latte.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Duration

class SuspendingRatelimit(private val burst: Int, private val duration: Duration) {
    @Volatile
    private var remainingQuota: Int = burst

    @Volatile
    private var resetTimestamp: Long = 0

    private val quotaRequestSem = Semaphore(1)

    fun overrideRatelimit(
        remainingQuota: Int,
        resetTimestamp: Long,
    ) {
        this.remainingQuota = remainingQuota
        this.resetTimestamp = resetTimestamp
    }

    private fun calculateWaitTime(): Long {
        return (resetTimestamp - System.currentTimeMillis()).coerceAtLeast(0)
    }

    private fun tryResetQuota() {
        if (System.currentTimeMillis() >= resetTimestamp) {
            remainingQuota = burst
            resetTimestamp = System.currentTimeMillis() + duration.inWholeMilliseconds
        }
    }

    suspend fun requestQuota() {
        quotaRequestSem.withPermit {
            if (remainingQuota <= 0) {
                val waitTime = calculateWaitTime()
                delay(waitTime)
            }
            tryResetQuota()

            check(remainingQuota > 0)
            remainingQuota--
        }
    }

    suspend fun tryRequestQuota(): Pair<Boolean, Long?> =
        quotaRequestSem.withPermit {
            if (remainingQuota <= 0) {
                val waitTime = calculateWaitTime()
                return@withPermit false to waitTime
            }
            tryResetQuota()

            check(remainingQuota > 0)
            remainingQuota--
            return@withPermit true to null
        }
}
