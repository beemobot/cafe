package gg.beemo.latte.broker.kafka

import gg.beemo.latte.CommonConfig
import gg.beemo.latte.broker.BaseBrokerMessageHeaders
import org.apache.kafka.common.header.Headers

class KafkaMessageHeaders(
    clientId: String,
    sourceCluster: String,
    targetClusters: Set<String>?,
    requestId: String?,
) : BaseBrokerMessageHeaders(
    clientId,
    sourceCluster,
    targetClusters,
    requestId
) {

    constructor(headers: Headers) : this(
        headers.getOrDefault(HEADER_CLIENT_ID, "default"),
        headers.getOrDefault(HEADER_SOURCE_CLUSTER, CommonConfig.INVALID_CLUSTER_ID),
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

}

private fun Headers.getOrDefault(key: String, defaultValue: String): String {
    return String(lastHeader(key)?.value() ?: defaultValue.toByteArray())
}
