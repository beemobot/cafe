package gg.beemo.latte.broker

class LocalConnection(
    override val serviceName: String,
    override val instanceId: String,
) : BrokerConnection() {

    override val supportsTopicHotSwap = true

    override suspend fun abstractSend(
        topic: String,
        key: String,
        value: String,
        headers: BrokerMessageHeaders,
    ): MessageId {
        if (shouldDispatchExternallyAfterShortCircuit(topic, key, value, headers)) {
            val isForExternalService =
                headers.targetServices.isNotEmpty() && headers.targetServices != setOf(serviceName)
            val isForExternalInstance =
                headers.targetInstances.isNotEmpty() && headers.targetInstances != setOf(instanceId)
            if (isForExternalService || isForExternalInstance) {
                throw IllegalArgumentException(
                    "Attempting to send message to other services/instances " +
                            "(services=${headers.targetServices}; instances=${headers.targetInstances}) " +
                            "in a LocalConnection"
                )
            }
        }

        return headers.messageId
    }

    override suspend fun start() {
        // Nothing to start :)
    }

    override fun createTopic(topic: String) {
        // noop
    }

    override fun removeTopic(topic: String) {
        // noop
    }

}
