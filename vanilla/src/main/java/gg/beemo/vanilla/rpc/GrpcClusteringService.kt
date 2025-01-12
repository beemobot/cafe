package gg.beemo.vanilla.rpc

import com.google.protobuf.Empty
import gg.beemo.latte.logging.Log
import gg.beemo.latte.proto.ClusteringGrpcKt
import gg.beemo.latte.proto.GetClusterConfigRequest
import gg.beemo.latte.proto.GetClusterConfigResponse
import gg.beemo.latte.proto.GuildState
import gg.beemo.latte.proto.LookupGuildClusterRequest
import gg.beemo.latte.proto.LookupGuildClusterResponse
import gg.beemo.latte.proto.ShardIdentifier
import gg.beemo.latte.proto.UpdateGuildStateRequest
import gg.beemo.latte.proto.getClusterConfigResponse
import gg.beemo.latte.proto.lookupGuildClusterResponse
import gg.beemo.latte.proto.shardIdentifier
import gg.beemo.vanilla.Config
import io.grpc.Status
import kotlinx.coroutines.flow.Flow
import java.util.HashMap
import kotlin.math.min

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
        this.clusters[request.clusterId] =
            ClusterConfig(
                clusterId = request.clusterId,
                grpcEndpoint = request.grpcEndpoint,
            )

        val clusterIndex = 0 // TODO map cluster id to index
        val shardRange = getClusterShardRange(clusterIndex, Config.TEA_SHARD_COUNT, Config.TEA_CLUSTER_COUNT)
        val shards =
            shardRange.map { shardId ->
                shardIdentifier {
                    this.clusterId = request.clusterId
                    this.shardId = shardId
                    this.shardCount = Config.TEA_SHARD_COUNT
                }
            }

        return getClusterConfigResponse {
            this.shards += shards
        }
    }

    override suspend fun updateGuildStates(requests: Flow<UpdateGuildStateRequest>): Empty {
        requests.collect { update ->
            val shard = update.shard
            log.debug(
                "Guild {} in Cluster {} Shard {}/{} has changed state to {}",
                update.guildId,
                shard.clusterId,
                shard.shardId,
                shard.clusterId,
                update.state,
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
        val guild = guilds[request.guildId] ?: throw Status.NOT_FOUND.withDescription("Guild not found").asRuntimeException()
        val cluster = clusters[guild.shard.clusterId] ?: throw Status.NOT_FOUND.withDescription("Cluster not found").asRuntimeException()
        return lookupGuildClusterResponse {
            this.clusterId = cluster.clusterId
            this.grpcEndpoint = cluster.grpcEndpoint
        }
    }

    private fun getClusterShardRange(
        cluster: Int,
        totalShards: Int,
        totalClusters: Int,
    ): IntRange {
        val numShardsForNormalCluster = totalShards / totalClusters
        val extraShards = totalShards % totalClusters

        // If the shard cluster is within the first 0 to (extraShards - 1) shard clusters,
        // we will allocate one of the extra shards to it.
        val numCommandedShards =
            if (extraShards > 0 && cluster < extraShards) {
                numShardsForNormalCluster + 1
            } else {
                numShardsForNormalCluster
            }

        val firstShardNumber =
            if (extraShards > 0) {
                cluster * numShardsForNormalCluster + min(cluster, extraShards - 1)
            } else {
                cluster * numShardsForNormalCluster
            }

        val lastShardNumber = firstShardNumber + numCommandedShards - 1

        return firstShardNumber..lastShardNumber
    }
}
