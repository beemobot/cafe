package gg.beemo.vanilla

import gg.beemo.latte.broker.BrokerClient
import gg.beemo.latte.broker.BrokerConnection
import gg.beemo.latte.broker.BrokerMessage
import gg.beemo.latte.logging.Log
import gg.beemo.latte.ratelimit.SharedRatelimitData
import gg.beemo.latte.ratelimit.SharedRatelimitData.RatelimitClientData
import gg.beemo.latte.util.SuspendingRatelimit
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// Give request expiry a bit of leeway in case of clock drift
private val EXPIRY_GRACE_PERIOD = 5.seconds.inWholeMilliseconds

class RatelimitClient(connection: BrokerConnection) : BrokerClient(connection) {

    private val log by Log
    private val globalRatelimitProvider = RatelimitProvider(50, 1.seconds)
    private val identifyRatelimitProvider = RatelimitProvider(1, 5.seconds)

    init {
        rpc<RatelimitClientData, Unit>(
            SharedRatelimitData.RATELIMIT_TOPIC,
            SharedRatelimitData.KEY_REQUEST_GLOBAL_QUOTA,
        ) {
            handleRatelimitRequest(it, globalRatelimitProvider, "global")
        }

        rpc<RatelimitClientData, Unit>(
            SharedRatelimitData.RATELIMIT_TOPIC,
            SharedRatelimitData.KEY_REQUEST_IDENTIFY_QUOTA,
        ) {
            handleRatelimitRequest(it, identifyRatelimitProvider, "identify")
        }
    }

    private suspend fun handleRatelimitRequest(
        msg: BrokerMessage<RatelimitClientData>,
        ratelimitProvider: RatelimitProvider,
        type: String,
    ) {
        val clientId = msg.value.discordClientId ?: "default"
        val service = "${msg.headers.sourceService}/${msg.headers.sourceInstance} (${clientId})"
        val expiresAt = msg.value.requestExpiresAt
        if (expiresAt != null && (expiresAt + EXPIRY_GRACE_PERIOD) < System.currentTimeMillis()) {
            log.info("Incoming expired $type quota request from service $service, ignoring")
            // If the request has already expired, ignore it to not eat quotas unnecessarily
            return
        }
        log.debug("Incoming {} quota request from service {}", type, service)
        ratelimitProvider.getClientRatelimit(clientId).requestQuota()
        log.debug("Granted {} quota request for service {}", type, service)
    }

}

private class RatelimitProvider(private val burst: Int, private val duration: Duration) {

    private val limiters = ConcurrentHashMap<String, SuspendingRatelimit>()

    fun getClientRatelimit(clientId: String): SuspendingRatelimit = limiters.computeIfAbsent(clientId) {
        SuspendingRatelimit(burst, duration)
    }

}
