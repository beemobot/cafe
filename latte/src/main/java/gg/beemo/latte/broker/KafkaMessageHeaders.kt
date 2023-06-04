package gg.beemo.latte.broker

import gg.beemo.latte.broker.IBrokerMessageHeaders.Companion.HEADER_CLIENT_ID
import gg.beemo.latte.broker.IBrokerMessageHeaders.Companion.HEADER_REQUEST_ID
import gg.beemo.latte.broker.IBrokerMessageHeaders.Companion.HEADER_SOURCE_CLUSTER
import gg.beemo.latte.broker.IBrokerMessageHeaders.Companion.HEADER_TARGET_CLUSTERS
import org.apache.kafka.common.header.Headers
import java.util.UUID

class KafkaMessageHeaders(
    override val clientId: String,
    override val sourceCluster: String,
    targetClusters: Set<String>?,
    requestId: String?,
) : IBrokerMessageHeaders {

    override val targetClusters: Set<String> = targetClusters ?: emptySet()
    override val requestId: String = requestId ?: UUID.randomUUID().toString()

    constructor(headers: Headers) : this(
        headers.getOrDefault(HEADER_CLIENT_ID, "default"),
        headers.getOrDefault(HEADER_SOURCE_CLUSTER, INVALID_CLUSTER_ID.toString()),
        headers.getOrDefault(HEADER_TARGET_CLUSTERS, "")
            .split(",")
            .filter { it.isNotEmpty() }
            .toSet(),
        headers.getOrDefault(HEADER_REQUEST_ID, ""),
    )

    fun applyTo(headers: Headers) {
        headers.add(HEADER_CLIENT_ID, clientId.toByteArray())
        headers.add(HEADER_TARGET_CLUSTERS, targetClusters.joinToString(",").toByteArray())
        headers.add(HEADER_REQUEST_ID, requestId.toByteArray())
        headers.add(HEADER_SOURCE_CLUSTER, sourceCluster.toByteArray())
    }

    companion object {

        const val INVALID_CLUSTER_ID = Integer.MIN_VALUE

    }

}

private fun Headers.getOrDefault(key: String, defaultValue: String): String {
    return String(lastHeader(key)?.value() ?: defaultValue.toByteArray())
}
