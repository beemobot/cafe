package gg.beemo.latte.broker

import gg.beemo.latte.logging.Log
import kotlinx.coroutines.*
import java.util.Collections

private class TopicMetadata(
    val topic: String,
    val keys: MutableMap<String, KeyMetadata>,
    var connectionListener: TopicListener? = null,
)

private class KeyMetadata(
    val topic: TopicMetadata,
    val producers: MutableSet<ProducerSubclient<*>>,
    val consumers: MutableSet<ConsumerSubclient<*>>,
)

abstract class BrokerClient(
    @PublishedApi
    internal val connection: BrokerConnection,
    private val consumerScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
) {

    private val log by Log
    private val topics: MutableMap<String, TopicMetadata> = Collections.synchronizedMap(HashMap())

    inline fun <reified T> consumer(
        topic: String,
        key: String,
        options: BrokerClientOptions = BrokerClientOptions(),
        noinline callback: suspend CoroutineScope.(BaseBrokerMessage<T>) -> Unit,
    ): ConsumerSubclient<T> {
        return consumer(topic, key, options, T::class.java, isTypeNullable<T>(), callback)
    }

    @PublishedApi
    internal fun <T> consumer(
        topic: String,
        key: String,
        options: BrokerClientOptions = BrokerClientOptions(),
        type: Class<T>,
        isNullable: Boolean,
        callback: suspend CoroutineScope.(BaseBrokerMessage<T>) -> Unit,
    ): ConsumerSubclient<T> {
        log.debug("Creating consumer for key '{}' in topic '{}' with type {}", key, topic, type.name)
        return ConsumerSubclient(connection, this, topic, key, options, type, isNullable, callback).also {
            registerConsumer(it)
        }
    }

    inline fun <reified T> producer(
        topic: String,
        key: String,
        options: BrokerClientOptions = BrokerClientOptions(),
    ): ProducerSubclient<T> {
        return producer(topic, key, options, T::class.java, isTypeNullable<T>())
    }

    @PublishedApi
    internal fun <T> producer(
        topic: String,
        key: String,
        options: BrokerClientOptions = BrokerClientOptions(),
        type: Class<T>,
        isNullable: Boolean,
    ): ProducerSubclient<T> {
        log.debug("Creating producer for key '{}' in topic '{}' with type {}", key, topic, type.name)
        return ProducerSubclient(connection, this, topic, key, options, type, isNullable).also {
            registerProducer(it)
        }
    }

    inline fun <reified RequestT, reified ResponseT> rpc(
        topic: String,
        key: String,
        options: BrokerClientOptions = BrokerClientOptions(),
        noinline callback: suspend CoroutineScope.(BaseRpcRequestMessage<RequestT, ResponseT>) -> RpcResponse<ResponseT>,
    ): RpcClient<RequestT, ResponseT> {
        return RpcClient(
            this,
            topic,
            key,
            options,
            RequestT::class.java,
            isTypeNullable<RequestT>(),
            ResponseT::class.java,
            isTypeNullable<ResponseT>(),
            callback,
        )
    }

    fun destroy(cancelScope: Boolean = true) {
        val producers = topics.values.flatMap { metadata -> metadata.keys.values.flatMap { it.producers } }
        val consumers = topics.values.flatMap { metadata -> metadata.keys.values.flatMap { it.consumers } }
        producers.forEach {
            it.destroy()
        }
        consumers.forEach {
            it.destroy()
        }
        topics.clear()
        if (cancelScope) {
            consumerScope.cancel()
        }
    }

    private fun registerProducer(producer: ProducerSubclient<*>) {
        val metadata = getOrCreateKeyMetadata(producer.topic, producer.key)
        metadata.producers.add(producer)
    }

    private fun registerConsumer(consumer: ConsumerSubclient<*>) {
        val metadata = getOrCreateKeyMetadata(consumer.topic, consumer.key)
        if (metadata.consumers.isEmpty() && metadata.topic.connectionListener == null) {
            // New consumer - create a new connection listener for this topic
            val listener = TopicListener { topic, key, value, headers ->
                onTopicMessage(topic, key, value, headers)
            }
            connection.on(consumer.topic, listener)
            metadata.topic.connectionListener = listener
        }
        metadata.consumers.add(consumer)
    }

    internal fun deregisterProducer(producer: ProducerSubclient<*>) {
        log.debug("Removing producer for key '{}' in topic '{}'", producer.key, producer.topic)
        val metadata = getExistingKeyMetadata(producer.topic, producer.key)
        metadata?.producers?.remove(producer)
    }

    internal fun deregisterConsumer(consumer: ConsumerSubclient<*>) {
        log.debug("Removing consumer for key '{}' in topic '{}'", consumer.key, consumer.topic)
        val metadata = getExistingKeyMetadata(consumer.topic, consumer.key)
        if (metadata?.consumers?.remove(consumer) == true && metadata.consumers.isEmpty()) {
            metadata.topic.connectionListener?.let {
                connection.off(metadata.topic.topic, it)
                metadata.topic.connectionListener = null
            }
        }
    }

    private fun getOrCreateKeyMetadata(topic: String, key: String): KeyMetadata {
        val topicData = topics.computeIfAbsent(topic) {
            TopicMetadata(topic, Collections.synchronizedMap(HashMap()))
        }
        val keyData = topicData.keys.computeIfAbsent(key) {
            KeyMetadata(topicData, Collections.synchronizedSet(HashSet()), Collections.synchronizedSet(HashSet()))
        }
        return keyData
    }

    private fun getExistingKeyMetadata(topic: String, key: String): KeyMetadata? {
        return topics[topic]?.keys?.get(key)
    }

    internal fun toResponseTopic(topic: String): String =
        if (connection.supportsTopicHotSwap) "$topic.responses" else topic

    internal fun toResponseKey(key: String): String = "$key.response"

    private fun onTopicMessage(
        topic: String,
        key: String,
        value: String,
        headers: BrokerMessageHeaders,
    ) {
        val metadata = getExistingKeyMetadata(topic, key) ?: return
        for (consumer in metadata.consumers) {
            consumerScope.launch {
                try {
                    consumer.onIncomingMessage(value, headers)
                } catch (e: Exception) {
                    log.error("Uncaught error in BrokerClient listener for key '$key' in topic '$topic'", e)
                }
            }
        }
    }

}

@PublishedApi
internal inline fun <reified T> isTypeNullable(): Boolean {
    return null is T || T::class.java == Unit::class.java || T::class.java == Void::class.java
}
