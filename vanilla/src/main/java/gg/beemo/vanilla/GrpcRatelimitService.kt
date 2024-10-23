package gg.beemo.vanilla

import gg.beemo.latte.logging.Log
import gg.beemo.latte.util.Ratelimit
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// TODO https://github.com/grpc/grpc-kotlin/blob/master/examples/server/src/main/kotlin/io/grpc/examples/animals/AnimalsServer.kt

class GrpcRatelimitService : RatelimitGrpcKt.RatelimitCoroutineImplBase() {

    private val log by Log
    private val globalRatelimits = RatelimitMap(50, 1.seconds)
    private val identifyRatelimits = RatelimitMap(1, 5.seconds)

    override suspend fun requestQuota(request: RatelimitRequest): RatelimitResponse {
        val (ratelimitMap, typeString) = when (request.type) {
            RatelimitType.GLOBAL -> globalRatelimits to "global"
            RatelimitType.IDENTIFY -> identifyRatelimits to "identify"
            else -> throw IllegalArgumentException("Unknown ratelimit type ${request.type}")
        }
        val ratelimit = ratelimitMap.getClientRatelimit(request.clientId)
        // TODO Do we want to make a blocking version?
        val (granted, resetAfter) = ratelimit.tryRequestQuota()
        log.debug("Got '{}' quota request from clientId {}, was tranted: {}", typeString, request.clientId, granted)

        return ratelimitResponse {
            this.granted = granted
            this.limit = ratelimit.burst
            this.remaining = ratelimit.remaining
            this.reset = ratelimit.resetAt
            this.resetAfter = resetAfter.toInt()
        }
    }

}

private class RatelimitMap(private val burst: Int, private val duration: Duration) {

    private val limiters = ConcurrentHashMap<Long, Ratelimit>()

    fun getClientRatelimit(clientId: Long): Ratelimit = limiters.computeIfAbsent(clientId) {
        Ratelimit(burst, duration)
    }

}
