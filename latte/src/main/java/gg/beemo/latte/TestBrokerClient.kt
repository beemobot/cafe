package gg.beemo.latte

import gg.beemo.latte.broker.BrokerClient
import gg.beemo.latte.broker.BrokerConnection
import gg.beemo.latte.broker.BrokerMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlin.time.Duration.Companion.seconds

class Tea {
    companion object {
        val cluster: Cluster = Cluster()
    }

    class Cluster {
        suspend fun restartShard(id: ShardId) {}
    }
}

class ShardId
class RaidUser
data class ShardRestartRequest(val shardId: ShardId)
data class GreetingRequest(val name: String)
data class GreetingResponse(val greeting: String)

// ------------------------------

class TestBrokerClient(connection: BrokerConnection) : BrokerClient(connection) {

    init {
        consumer<ShardRestartRequest>(
            topic = "cluster.shard",
            key = "restart",
        ) {
            Tea.cluster.restartShard(it.value.shardId)
        }
    }

    private val raidBanQueue = producer<RaidUser>(
        topic = "raid.bans",
        key = "ban.enqueue",
    )

    private val greetingRpc = rpc<GreetingRequest, GreetingResponse>(
        topic = "rpc.greetings",
        key = "greeting.requests",
    ) {
        delay(2.seconds)
        return@rpc GreetingResponse("Hello, ${it.value.name}")
    }

    private val nullRpc = rpc<Unit?, Unit?>(
        topic = "null",
        key = "null",
    ) {
        if (it.value != null) {
            throw IllegalStateException("nullRpc received non-null")
        }
        return@rpc null
    }

    suspend fun enqueueRaidBan(user: RaidUser) {
        raidBanQueue.send(user)
    }

    suspend fun sendNull() {
        val definitelyNull = nullRpc.call(null)
        val value = definitelyNull.value
        if (value != null) {
            throw IllegalStateException("nullRpc returned non-null")
        }
    }

    suspend fun createGreeting(name: String): String {
        val response = greetingRpc.call(
            GreetingRequest(name),
            services = setOf(CommonConfig.BrokerServices.TEA),
            instances = setOf("0"),
            timeout = 5.seconds,
        )
        return response.value.greeting
    }

    suspend fun collectGreetings(name: String): List<String> {
        val flow = greetingRpc.stream(GreetingRequest(name))
        return flow.map { it.value.greeting }.toList()
    }

}
