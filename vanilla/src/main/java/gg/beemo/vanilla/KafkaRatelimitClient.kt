package gg.beemo.vanilla

import gg.beemo.latte.broker.BrokerClient
import gg.beemo.latte.broker.BrokerConnection
import gg.beemo.latte.broker.IgnoreRpcRequest
import gg.beemo.latte.broker.rpc.RpcStatus
import gg.beemo.latte.logging.Log
import gg.beemo.latte.ratelimit.SharedRatelimitData
import gg.beemo.latte.util.Ratelimit
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// Give request expiry a bit of leeway in case of clock drift
private val EXPIRY_GRACE_PERIOD = 5.seconds.inWholeMilliseconds

class KafkaRatelimitClient(connection: BrokerConnection) : BrokerClient(connection) {

    private val log by Log
    private val globalRatelimitProvider = KafkaRatelimitProvider(50, 1.seconds)
    private val identifyRatelimitProvider = KafkaRatelimitProvider(1, 5.seconds)

    init {
        rpc<SharedRatelimitData.RatelimitRequestData, Unit>(
            SharedRatelimitData.RATELIMIT_TOPIC,
            SharedRatelimitData.KEY_REQUEST_QUOTA,
        ) {
            val msg = it.value
            val type = msg.type
            val clientId = msg.discordClientId ?: "default"
            val service = "${it.headers.sourceService}/${it.headers.sourceInstance} (${clientId})"
            val expiresAt = msg.requestExpiresAt

            if (expiresAt != null && (expiresAt + EXPIRY_GRACE_PERIOD) < System.currentTimeMillis()) {
                log.info("Incoming expired $type quota request from service $service, ignoring")
                // If the request has already expired, ignore it to not eat quotas unnecessarily
                throw IgnoreRpcRequest()
            }

            log.debug("Incoming {} quota request from service {}", type, service)
            val provider = when (msg.type) {
                SharedRatelimitData.RatelimitType.GLOBAL -> globalRatelimitProvider
                SharedRatelimitData.RatelimitType.IDENTIFY -> identifyRatelimitProvider
                else -> throw IllegalArgumentException("Unknown ratelimit type ${msg.type}")
            }

            provider.getClientRatelimit(clientId).requestQuota()

            log.debug("Granted {} quota request for service {}", type, service)

            return@rpc RpcStatus.OK to Unit
        }
    }

}

private class KafkaRatelimitProvider(private val burst: Int, private val duration: Duration) {

    private val limiters = ConcurrentHashMap<String, Ratelimit>()

    fun getClientRatelimit(clientId: String): Ratelimit = limiters.computeIfAbsent(clientId) {
        Ratelimit(burst, duration)
    }

}
