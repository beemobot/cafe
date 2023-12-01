package gg.beemo.latte.broker

import java.util.*

open class BaseBrokerMessageHeaders(
    val clientId: String?,
    val sourceService: String,
    val sourceInstance: String?,
    val targetServices: Set<String>,
    val targetInstances: Set<String>,
    val inReplyTo: String? = null,
) {

    val messageId = UUID.randomUUID().toString()

    companion object {

        const val HEADER_CLIENT_ID = "client-id"
        const val HEADER_REQUEST_ID = "request-id"
        const val HEADER_SOURCE_CLUSTER = "source-cluster"
        const val HEADER_TARGET_CLUSTERS = "target-clusters"

    }

}
