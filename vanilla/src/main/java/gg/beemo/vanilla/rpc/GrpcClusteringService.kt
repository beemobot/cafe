package gg.beemo.vanilla.rpc

import com.google.protobuf.Empty
import gg.beemo.latte.logging.Log
import gg.beemo.latte.proto.ClusterConfigRequest
import gg.beemo.latte.proto.ClusterConfigResponse
import gg.beemo.latte.proto.clusterConfigResponse
import gg.beemo.latte.proto.ClusteringGrpcKt
import gg.beemo.latte.proto.GetClusterConfigRequest
import gg.beemo.latte.proto.GetClusterConfigResponse
import gg.beemo.latte.proto.GuildState
import gg.beemo.latte.proto.LookupGuildClusterRequest
import gg.beemo.latte.proto.LookupGuildClusterResponse
import gg.beemo.latte.proto.ShardIdentifier
import gg.beemo.latte.proto.shardIdentifier
import gg.beemo.latte.proto.UpdateGuildStateRequest
import gg.beemo.latte.proto.getClusterConfigResponse
import gg.beemo.latte.proto.lookupGuildClusterResponse
import kotlinx.coroutines.flow.Flow
import java.util.HashMap

data class ClusterConfig(
    val clusterId: String,
    val grpcEndpoint: String,
)

data class GuildStatus(
    val guildId: Long,
    val shard: ShardIdentifier,
    val state: GuildState,
)

class GrpcClusteringService : ClusteringGrpcKt.ClusteringCoroutineImplBase() {

    private val log by Log

    private val clusters = HashMap<String, ClusterConfig>()
    private val guilds = HashMap<Long, GuildStatus>()

    override suspend fun getClusterConfig(request: GetClusterConfigRequest): GetClusterConfigResponse {
        log.info("Received cluster config request from cluster ID '${request.clusterId}'")
        this.clusters[request.clusterId] = ClusterConfig(
            clusterId = request.clusterId,
            grpcEndpoint = request.grpcEndpoint,
        )
        // TODO Return correct shard mapping
        return getClusterConfigResponse {
            this.shards += listOf(
                shardIdentifier {
                    this.clusterId = "lol"
                    this.shardId = 0
                    this.shardCount = 1
                },
            )
        }
    }

    override suspend fun updateGuildStates(requests: Flow<UpdateGuildStateRequest>): Empty {
        requests.collect { update ->
            val shard = update.shard
            log.debug(
                "Guild {} in Cluster {} Shard {}/{} has changed state to {}",
                update.guildId, shard.clusterId, shard.shardId, shard.clusterId, update.state,
            )
            if (!clusters.containsKey(shard.clusterId)) {
                log.warn("Unknown cluster {} in guild update for {}", shard.clusterId, update.guildId)
            }
            if (update.state == GuildState.DELETED) {
                guilds.remove(update.guildId)
            } else {
                guilds[update.guildId] = GuildStatus(guildId = update.guildId, shard = shard, state = update.state)
            }
        }
        return Empty.getDefaultInstance()
    }

    override suspend fun lookupGuildCluster(request: LookupGuildClusterRequest): LookupGuildClusterResponse {
        val guild = guilds[request.guildId]
        requireNotNull(guild) // TODO How to properly return errors in gRPC?
        val cluster = clusters[guild.shard.clusterId]
        requireNotNull(cluster) // TODO Same as above
        return lookupGuildClusterResponse {
            this.clusterId = cluster.clusterId
            this.grpcEndpoint = cluster.grpcEndpoint
        }
    }

}
