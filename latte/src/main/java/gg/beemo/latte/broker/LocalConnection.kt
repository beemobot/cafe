package gg.beemo.latte.broker

class LocalConnection : BrokerConnection() {

    override val clientId = "local-client-id"
    override val clusterId = "local-cluster-id"

    override suspend fun send(
        topic: String,
        key: String,
        value: String,
        headers: BaseBrokerMessageHeaders,
        blocking: Boolean,
    ): String {
        if (!maybeShortCircuitOutgoingMessage(topic, key, value, headers)) {
            throw IllegalArgumentException("Attempting to send message to other cluster(s) [${headers.targetClusters}] in a LocalConnection")
        }

        return headers.requestId
    }

    override fun start() {
        // Nothing to start :)
    }

    override fun createHeaders(targetClusters: Set<String>?, requestId: String?): BaseBrokerMessageHeaders {
        return BaseBrokerMessageHeaders(this.clientId, this.clusterId, targetClusters, requestId)
    }

}
