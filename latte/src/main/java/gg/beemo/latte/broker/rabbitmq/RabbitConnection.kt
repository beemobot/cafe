package gg.beemo.latte.broker.rabbitmq

import com.rabbitmq.client.Address
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import gg.beemo.latte.broker.BrokerConnection
import gg.beemo.latte.broker.BrokerMessageHeaders
import gg.beemo.latte.broker.MessageId
import gg.beemo.latte.logging.Log
import java.util.Collections

// TODO Implementation considerations:
//  - Channels are not thread-safe (for sending); need to create a coroutine-wrapper around them
//  - "Consuming in one thread and publishing in another thread on a shared channel can be safe."

class RabbitConnection(
    rabbitHosts: Array<String>,
    override val serviceName: String,
    override val instanceId: String,
    private val useTls: Boolean = false,
) : BrokerConnection() {

    override val supportsTopicHotSwap = true
    private val rabbitAddresses = rabbitHosts.map(Address::parseAddress)
    private val log by Log

    private var connection: Connection? = null
    private val channels = Collections.synchronizedMap(HashMap<String, Channel>())

    override suspend fun start() {
        connection = ConnectionFactory().apply {
            if (useTls) {
                // TODO This will trust every cert, even self-signed ones
                useSslProtocol()
            }
        }.newConnection(rabbitAddresses, instanceId)
        // TODO Create exchange
    }

    override suspend fun abstractSend(
        topic: String,
        key: String,
        value: String,
        headers: BrokerMessageHeaders
    ): MessageId {
        TODO()
    }

    override fun destroy() {
        log.debug("Destroying RabbitConnection")
        connection?.close()
        connection = null
        super.destroy()
    }

    override fun createTopic(topic: String) {
        val channel = channels.computeIfAbsent(topic) {
            val connection = checkNotNull(connection) { "Connection not open" }
            connection.createChannel()
        }
        // TODO Consume
    }

    override fun removeTopic(topic: String) {
        val channel = channels.remove(topic)
        channel?.close()
    }

}
