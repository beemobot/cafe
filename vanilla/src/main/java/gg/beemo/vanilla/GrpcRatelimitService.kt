package gg.beemo.vanilla

import gg.beemo.latte.logging.Log
import gg.beemo.vanilla.proto.RatelimitGrpcKt
import gg.beemo.vanilla.proto.RatelimitQuota
import gg.beemo.vanilla.proto.ratelimitQuota
import gg.beemo.vanilla.proto.RatelimitRequest
import gg.beemo.vanilla.proto.RatelimitType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class GrpcRatelimitService : RatelimitGrpcKt.RatelimitCoroutineImplBase() {

    private val log by Log
    private val globalRatelimits = ClientRatelimits(50, 1.seconds)
    private val identifyRatelimits = ClientRatelimits(1, 5.seconds)

    override suspend fun reserveQuota(request: RatelimitRequest): RatelimitQuota {
        val (ratelimitMap, typeString) = when (request.type) {
            RatelimitType.GLOBAL -> globalRatelimits to "global"
            RatelimitType.IDENTIFY -> identifyRatelimits to "identify"
            else -> throw IllegalArgumentException("Unknown ratelimit type ${request.type}")
        }

        val ratelimit = ratelimitMap.getClientRatelimit(request.clientId)
        val maxDelay = if (request.hasMaxDelay()) request.maxDelay.toLong() else null
        val (granted, at) = ratelimit.reserveQuota(request.probeOnly, maxDelay)

        if (request.probeOnly) {
            log.debug("Probed {} quota slot for clientId {} is at {}", typeString, request.clientId, at)
        } else if (granted) {
            log.debug("Reserved {} quota slot for clientId {} at {}", typeString, request.clientId, at)
        } else {
            val maxTimestamp = if (maxDelay != null) System.currentTimeMillis() + maxDelay else null
            log.debug(
                "Failed to reserve {} quota slot for clientId {}, next slot would be at {}, requested max delay was {} (-> {})",
                typeString,
                request.clientId,
                at,
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
