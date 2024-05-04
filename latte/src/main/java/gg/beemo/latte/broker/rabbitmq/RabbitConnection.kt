package gg.beemo.latte.broker.rabbitmq

import com.rabbitmq.client.*
import gg.beemo.latte.broker.BrokerConnection
import gg.beemo.latte.broker.BrokerMessageHeaders
import gg.beemo.latte.broker.MessageId
import gg.beemo.latte.logging.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean


private class ChannelData(
    val channel: Channel,
    val sendMutex: Mutex = Mutex(),
    var isConsuming: AtomicBoolean = AtomicBoolean(false),
    var consumerTag: String? = null,
)

// TODO Temporary keys/topics created by RPC clients should ideally be cached and re-used,
//  instead of being destroyed and recreated every time.

class RabbitConnection(
    rabbitHosts: Array<String>,
    override val serviceName: String,
    override val instanceId: String,
    private val useTls: Boolean = false,
    private val username: String = "guest",
    private val password: String = "guest",
) : BrokerConnection() {

    override val supportsTopicHotSwap = true
    override val deferInitialTopicCreation = true
    private val rabbitAddresses = rabbitHosts.map(Address::parseAddress)
    private val log by Log

    private var connection: Connection? = null
    private val channels = Collections.synchronizedMap(HashMap<String, ChannelData>())

    override suspend fun abstractStart() {
        connection = ConnectionFactory().also {
            if (useTls) {
                // TODO This will trust every cert, even self-signed ones
                it.useSslProtocol()
            }
            it.useNio()
            it.username = username
            it.password = password
            // TODO Investogate more properties, such as client-provided name
        }.newConnection(rabbitAddresses, instanceId)
    }

    override suspend fun abstractSend(
        topic: String,
        key: String,
        value: String,
        headers: BrokerMessageHeaders
    ): MessageId {
        if (shouldDispatchExternallyAfterShortCircuit(topic, key, value, headers)) {

            val channelData = getOrCreateChannel(topic)
            // RabbitMQ's channels are not thread-safe for sending. Consuming and sending
            // through the same channel at the same time is fine though.
            channelData.sendMutex.withLock {
                val properties = AMQP.BasicProperties.Builder().apply {
                    // https://www.rabbitmq.com/docs/publishers#message-properties
                    deliveryMode(2) // Persistent
                    headers(headers.headers) // lol
                }.build()
                // TODO Later, we can set mandatory=true here and set up a dead letter exchange.
                //  This would be especially useful for raid bans, so that they don't get lost.
                //  Though iirc they should be queued, no? Investigate queueing behavior, again.
                channelData.channel.basicPublish(topic, key, properties, value.toByteArray())
            }

        }

        return headers.messageId
    }

    override fun destroy() {
        log.debug("Destroying RabbitConnection")
        connection?.close()
        connection = null
        super.destroy()
    }

    override fun createTopic(topic: String) {
        val channelData = getOrCreateChannel(topic)
        if (channelData.isConsuming.getAndSet(true)) {
            return
        }
        val consumer = object : DefaultConsumer(channelData.channel) {

            override fun handleDelivery(
                consumerTag: String,
                envelope: Envelope,
                properties: AMQP.BasicProperties,
                body: ByteArray
            ) {
                val key = envelope.routingKey ?: ""
                val value = String(body)
                val headers = BrokerMessageHeaders(properties.headers.mapValues { it.value.toString() })
                dispatchIncomingMessage(topic, key, value, headers)
                channel.basicAck(envelope.deliveryTag, false)
            }

            override fun handleShutdownSignal(consumerTag: String, sig: ShutdownSignalException) {
                if (sig.isInitiatedByApplication) {
                    return
                }
                log.error("RabbitMQ consumer for topic $topic has shut down unexpectedly", sig)
                // TODO Automatic Reconnect?
            }
        }
        channelData.consumerTag = channelData.channel.basicConsume(createQueueName(topic), false, consumer)
    }

    override fun removeTopic(topic: String) {
        val channel = channels.remove(topic)
        channel?.channel?.queueDelete(createQueueName(topic))
        channel?.channel?.close()
    }

    private fun getOrCreateChannel(topic: String): ChannelData {
        return channels.computeIfAbsent(topic) {
            val connection = checkNotNull(connection) { "Connection not open" }
            val channel = connection.createChannel().apply {
                val exchangeName = topic
                val queueName = createQueueName(topic)
                val routingKey = "#"
                exchangeDeclare(exchangeName, BuiltinExchangeType.TOPIC, true)
                queueDeclare(queueName, true, false, false, null)
                queueBind(queueName, exchangeName, routingKey)
            }
            ChannelData(channel)
        }
    }

    private fun createQueueName(topic: String) = "$serviceName.$instanceId.$topic"

}
