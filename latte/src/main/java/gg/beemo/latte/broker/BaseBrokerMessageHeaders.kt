package gg.beemo.latte.broker

import java.util.*

open class BaseBrokerMessageHeaders(
    val clientId: String,
    val sourceCluster: String,
    targetClusters: Set<String>?, // // Empty = No target restriction
    requestId: String?,
) {

    val targetClusters: Set<String> = targetClusters ?: emptySet()
    val requestId: String = requestId ?: UUID.randomUUID().toString()

    companion object {

        const val HEADER_CLIENT_ID = "client-id"
        const val HEADER_REQUEST_ID = "request-id"
        const val HEADER_SOURCE_CLUSTER = "source-cluster"
        const val HEADER_TARGET_CLUSTERS = "target-clusters"

    }

}
