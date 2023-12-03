package gg.beemo.latte.broker

class LocalConnection : BrokerConnection() {

    override val serviceName = "local-service-name"
    override val instanceId = "local-instance"
    override val supportsTopicHotSwap = true

    override suspend fun send(
        topic: String,
        key: String,
        value: String,
        headers: BaseBrokerMessageHeaders,
    ): MessageId {
        if (shouldDispatchExternallyAfterShortCircuit(topic, key, value, headers) &&
            (headers.targetServices.isNotEmpty() || headers.targetInstances.isNotEmpty())
        ) {
            throw IllegalArgumentException(
                "Attempting to send message to other services/instances " +
                        "(services=${headers.targetServices}; instances=${headers.targetInstances}) " +
                        "in a LocalConnection"
            )
        }

        return headers.messageId
    }

    override suspend fun start() {
        // Nothing to start :)
    }

    override fun createHeaders(
        targetServices: Set<String>,
        targetInstances: Set<String>,
        inReplyTo: MessageId?
    ): BaseBrokerMessageHeaders {
        return BaseBrokerMessageHeaders(serviceName, instanceId, targetServices, targetInstances, inReplyTo, null)
    }

    override fun createTopic(topic: String) {
        // noop
    }

    override fun removeTopic(topic: String) {
        // noop
    }

}
