package gg.beemo.latte.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Duration

class Ratelimit(val burst: Int, val duration: Duration) {
    @Volatile
    private var remainingQuota: Int = burst
    val remaining: Int get() = remainingQuota

    @Volatile
    private var resetTimestamp: Long = 0
    val resetAt: Long get() = resetTimestamp

    private val quotaRequestSem = Semaphore(1)

    suspend fun requestQuota() {
        quotaRequestSem.withPermit {
            if (remainingQuota <= 0) {
                tryResetQuota()
                val waitTime = calculateWaitTime()
                delay(waitTime)
            }
            tryResetQuota()
            check(remainingQuota > 0)
            remainingQuota--
        }
    }

    fun tryRequestQuota(): Pair<Boolean, Long> {
        tryResetQuota()
        if (remainingQuota <= 0) {
            return false to calculateWaitTime()
        }
        check(remainingQuota > 0)
        remainingQuota--
        return true to calculateWaitTime()
    }

    fun addQuota(amount: Int) {
        remainingQuota += amount
    }

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
}
