package gg.beemo.latte

import gg.beemo.latte.broker.BrokerClient
import gg.beemo.latte.broker.BrokerConnection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlin.time.Duration.Companion.seconds

// TODO This would be in the CommonConfig or similar.
object BrokerServices {
    const val TEA = "tea"
}

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
        ) { req: ShardRestartRequest ->
            Tea.cluster.restartShard(req.shardId)
        }
    }

    private val raidBanQueue = producer<RaidUser>(
        topic = "raid.bans",
        key = "ban.enqueue",
    )

    private val greetingRpc = rpc<GreetingRequest, GreetingResponse>(
        topic = "rpc.greetings",
        key = "greeting.requests",
    ) { req: GreetingRequest ->
        delay(2.seconds)
        return@rpc GreetingResponse("Hello, ${req.name}")
    }

    suspend fun enqueueRaidBan(user: RaidUser) {
        raidBanQueue.send(user)
    }

    suspend fun createGreeting(name: String): String {
        // TODO Have to specify what cluster to send to.
        //  Must also support external clusters such as milk... which aren't called clusters.
        //  Maybe I should rename it to "service", so you specify the "target service".
        //  Perhaps also the "target instance"? Because Tea as a whole would be a service.
        //  So for a cluster-specific request, you send to service=tea, instance=0.
        //  This is something that needs to be adapted at the connection level as well.
        val response = greetingRpc.call(
            GreetingRequest(name),
            services = setOf(BrokerServices.TEA),
            instances = setOf("0"),
            timeout = 5.seconds,
        )
        return response.greeting
    }

    suspend fun collectGreetings(name: String): List<String> {
        val flow = greetingRpc.stream(GreetingRequest(name))
        return flow.map { it.greeting }.toList()
    }

}
