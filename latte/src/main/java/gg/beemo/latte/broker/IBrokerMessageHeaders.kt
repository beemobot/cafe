package gg.beemo.latte.broker

interface IBrokerMessageHeaders {

    val clientId: String
    val sourceCluster: String
    val targetClusters: Set<String> // // Empty = No target restriction
    val requestId: String

    companion object {

        const val HEADER_CLIENT_ID = "client-id"
        const val HEADER_REQUEST_ID = "request-id"
        const val HEADER_SOURCE_CLUSTER = "source-cluster"
        const val HEADER_TARGET_CLUSTERS = "target-clusters"

    }

}
