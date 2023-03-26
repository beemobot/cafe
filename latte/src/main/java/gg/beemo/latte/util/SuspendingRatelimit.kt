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

    fun overrideRatelimit(remainingQuota: Int, resetTimestamp: Long) {
        this.remainingQuota = remainingQuota
        this.resetTimestamp = resetTimestamp
    }

    suspend fun requestQuota() {
        quotaRequestSem.withPermit {
            if (remainingQuota <= 0) {
                val waitTime = (resetTimestamp - System.currentTimeMillis()).coerceAtLeast(0)
                delay(waitTime)
            }
            if (System.currentTimeMillis() >= resetTimestamp) {
                remainingQuota = burst
                resetTimestamp = System.currentTimeMillis() + duration.inWholeMilliseconds
            }
            check(remainingQuota > 0)
            remainingQuota--
        }
    }

}
