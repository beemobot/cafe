package gg.beemo.vanilla

import gg.beemo.latte.broker.BrokerClient
import gg.beemo.latte.broker.BrokerConnection
import gg.beemo.latte.broker.BrokerMessage
import gg.beemo.latte.logging.log
import gg.beemo.latte.ratelimit.SharedRatelimitData
import gg.beemo.latte.ratelimit.SharedRatelimitData.RatelimitClientData
import gg.beemo.latte.util.SuspendingRatelimit
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// Give request expiry a bit of leeway in case of clock drift
private val EXPIRY_GRACE_PERIOD = 5.seconds.inWholeMilliseconds

class RatelimitClient(conn: BrokerConnection) : BrokerClient<RatelimitClientData>(
    conn,
    RatelimitClientData::class.java,
    SharedRatelimitData.RATELIMIT_TOPIC,
) {

    private val globalRatelimitProvider = RatelimitProvider(50, 1.seconds)
    private val identifyRatelimitProvider = RatelimitProvider(1, 5.seconds)

    init {
        on(SharedRatelimitData.KEY_REQUEST_GLOBAL_QUOTA) { msg ->
            handleRatelimitRequest(msg, globalRatelimitProvider, "global")
        }

        on(SharedRatelimitData.KEY_REQUEST_IDENTIFY_QUOTA) { msg ->
            handleRatelimitRequest(msg, identifyRatelimitProvider, "identify")
        }
    }

    private suspend fun handleRatelimitRequest(
        msg: BrokerMessage<RatelimitClientData>,
        ratelimitProvider: RatelimitProvider,
        type: String,
    ) {
        val sourceCluster = msg.headers.sourceCluster
        val client = msg.headers.clientId
        val expiresAt = msg.value?.requestExpiresAt
        if (expiresAt != null && (expiresAt + EXPIRY_GRACE_PERIOD) < System.currentTimeMillis()) {
            log.info("Incoming expired '$type' quota request from client '$client' in cluster $sourceCluster, ignoring")
            // If the request has already expired, ignore it to not eat quotas unnecessarily
            return
        }
        log.debug("Incoming '$type' quota request from client '$client' in cluster $sourceCluster")
        ratelimitProvider.getClientRatelimit(client).requestQuota()
        log.debug("Granted '$type' quota request for client '$client' in cluster $sourceCluster")
        msg.respond(null, false)
    }

}

private class RatelimitProvider(private val burst: Int, private val duration: Duration) {

    private val limiters = ConcurrentHashMap<String, SuspendingRatelimit>()

    fun getClientRatelimit(client: String): SuspendingRatelimit = limiters.computeIfAbsent(client) {
        SuspendingRatelimit(burst, duration)
    }

}
