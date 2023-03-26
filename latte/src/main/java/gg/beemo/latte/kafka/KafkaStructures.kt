package gg.beemo.latte.kafka

import org.apache.kafka.common.header.Headers
import java.util.UUID

private const val HEADER_CLIENT_ID = "client-id"
private const val HEADER_REQUEST_ID = "request-id"
private const val HEADER_SOURCE_CLUSTER = "source-cluster"
private const val HEADER_TARGET_CLUSTERS = "target-clusters"

data class KafkaRecordHeaders(
    val clientId: String,
    val sourceCluster: Int,
    val targetClusters: Set<Int>, // Empty = No target restriction
    val requestId: String,
) {

    constructor(clientId: String, sourceCluster: Int) : this(
        clientId,
        sourceCluster,
        emptySet(),
    )

    constructor(clientId: String, sourceCluster: Int, targetClusters: Set<Int>) : this(
        clientId,
        sourceCluster,
        targetClusters,
        UUID.randomUUID().toString(),
    )

    constructor(headers: Headers) : this(
        headers.getOrDefault(HEADER_CLIENT_ID, "default"),
        headers.getOrDefault(HEADER_SOURCE_CLUSTER, INVALID_CLUSTER_ID.toString()).toInt(),
        headers.getOrDefault(HEADER_TARGET_CLUSTERS, "")
            .split(",")
            .filter { it.isNotEmpty() }
            .map { it.toInt() }
            .toSet(),
        headers.getOrDefault(HEADER_REQUEST_ID, ""),
    )

    fun applyTo(headers: Headers) {
        headers.add(HEADER_CLIENT_ID, clientId.toByteArray())
        headers.add(HEADER_TARGET_CLUSTERS, targetClusters.joinToString(",").toByteArray())
        headers.add(HEADER_REQUEST_ID, requestId.toByteArray())
        headers.add(HEADER_SOURCE_CLUSTER, "$sourceCluster".toByteArray())
    }

    companion object {

        const val INVALID_CLUSTER_ID = Integer.MIN_VALUE

    }

}

private fun Headers.getOrDefault(key: String, defaultValue: String): String {
    return String(lastHeader(key)?.value() ?: defaultValue.toByteArray())
}

data class KafkaMessage<T : Any>(
    val client: KafkaClient<T>,
    val key: String,
    val value: T?,
    val headers: KafkaRecordHeaders
) {

    suspend fun respond(data: T?, blocking: Boolean = true) {
        client.respond(this, data, blocking)
    }

}
