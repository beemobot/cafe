package gg.beemo.vanilla

import gg.beemo.latte.logging.Log
import gg.beemo.latte.proto.RatelimitGrpcKt
import gg.beemo.latte.proto.RatelimitQuota
import gg.beemo.latte.proto.ratelimitQuota
import gg.beemo.latte.proto.RatelimitRequest
import gg.beemo.latte.proto.RatelimitType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class GrpcRatelimitService : RatelimitGrpcKt.RatelimitCoroutineImplBase() {

    private val log by Log
    private val globalRatelimits = ClientRatelimits(50, 1.seconds)
    private val identifyRatelimits = ClientRatelimits(1, 5.seconds)

    override suspend fun reserveQuota(request: RatelimitRequest): RatelimitQuota {
        val clientRatelimits = when (request.type) {
            RatelimitType.GLOBAL -> globalRatelimits
            RatelimitType.IDENTIFY -> identifyRatelimits
            else -> throw IllegalArgumentException("Unknown ratelimit type ${request.type}")
        }

        val ratelimit = clientRatelimits.getClientRatelimit(request.clientId)
        val maxDelay = if (request.hasMaxDelay()) request.maxDelay.toLong() else null
        val (granted, at) = ratelimit.reserveQuota(request.probeOnly, maxDelay)
        val delay = (at - System.currentTimeMillis()).coerceAtLeast(0)

        if (request.probeOnly) {
            log.debug(
                "Probed {} quota slot for clientId {} is at {} (in {} ms)",
                request.type,
                request.clientId,
                at,
                delay
            )
        } else if (granted) {
            log.debug(
                "Reserved {} quota slot for clientId {} at {} (in {} ms)",
                request.type,
                request.clientId,
                at,
                delay
            )
        } else {
            val maxTimestamp = if (maxDelay != null) System.currentTimeMillis() + maxDelay else null
            log.debug(
                "Failed to reserve {} quota slot for clientId {}, next slot would be at {} (in {} ms), requested max delay was {} (-> {})",
                request.type,
                request.clientId,
                at,
                delay,
                maxDelay,
                maxTimestamp
            )
        }

        return ratelimitQuota {
            this.granted = granted
            this.at = at
        }
    }

}

private class ClientRatelimits(private val burst: Int, private val duration: Duration) {

    private val limiters = ConcurrentHashMap<Long, RatelimitQueue>()

    fun getClientRatelimit(clientId: Long): RatelimitQueue = limiters.computeIfAbsent(clientId) {
        RatelimitQueue(burst, duration)
    }

}

data class RatelimitSlot(
    var usedQuota: Int,
    val startsAt: Long,
    val endsAt: Long,
)

private class RatelimitQueue(private val burst: Int, private val duration: Duration) {

    private val queue = LinkedList<RatelimitSlot>()
    private val lock = Mutex()

    suspend fun reserveQuota(probeOnly: Boolean = false, maxDelay: Long? = null): Pair<Boolean, Long> =
        lock.withLock {
            val now = System.currentTimeMillis()

            // Clean up expired slots
            while (queue.isNotEmpty() && now > queue.first.endsAt) {
                queue.removeFirst()
            }

            // Find free slot at the end of the queue
            val lastSlot = queue.lastOrNull()
            // No slots are used, so ratelimit is immediately available
            if (lastSlot == null) {
                // No timeout to check if we can immediately grant quota
                if (probeOnly) {
                    return@withLock false to 0
                }
                queue.add(RatelimitSlot(1, now, now + duration.inWholeMilliseconds))
                return@withLock true to 0
            }

            // Check if slot still has quota available
            if (lastSlot.usedQuota < burst) {
                val exceedsDelay = maxDelay != null && lastSlot.startsAt > now + maxDelay
                if (exceedsDelay || probeOnly) {
                    return@withLock false to lastSlot.startsAt
                }
                lastSlot.usedQuota++
                return@withLock true to lastSlot.startsAt
            }

            // Slot is full, create new slot
            val exceedsDelay = maxDelay != null && lastSlot.endsAt > now + maxDelay
            if (exceedsDelay || probeOnly) {
                return@withLock false to lastSlot.endsAt
            }
            val nextStart = lastSlot.endsAt
            val nextEnd = nextStart + duration.inWholeMilliseconds
            queue.add(RatelimitSlot(1, nextStart, nextEnd))
            return@withLock true to nextStart
        }

}
