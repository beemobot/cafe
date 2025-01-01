package gg.beemo.latte.broker

import gg.beemo.latte.broker.rpc.BaseRpcRequestMessage
import gg.beemo.latte.broker.rpc.RpcClient
import gg.beemo.latte.broker.rpc.RpcResponse
import gg.beemo.latte.logging.Log
import kotlinx.coroutines.*
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean


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
            registerSubclient(it)
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
            registerSubclient(it)
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

    private fun registerSubclient(subclient: BaseSubclient) {
        val topic = subclient.topic
        val metadata = topics.computeIfAbsent(topic) {
            TopicMetadata(connection, consumerScope, topic)
        }
        metadata.registerSubclient(subclient)
    }

    internal fun deregisterSubclient(subclient: BaseSubclient) {
        val topic = subclient.topic
        topics[topic]?.let {
            it.deregisterSubclient(subclient)
            if (it.isEmpty) {
                it.destroy()
                topics.remove(topic)
            }
        }
    }

    fun destroy(cancelScope: Boolean = true) {
        log.debug("Destroying BrokerClient of type {} with active topics {}", javaClass.simpleName, topics.keys)
        while (topics.isNotEmpty()) {
            val topic = topics.keys.first()
            topics[topic]?.destroy()
            topics.remove(topic)
        }
        if (cancelScope) {
            consumerScope.cancel()
        }
    }

}

private class TopicMetadata(
    private val connection: BrokerConnection,
    private val consumerScope: CoroutineScope,
    private val topic: String,
) {

    private val log by Log
    private val _keys: MutableMap<String, KeyMetadata> = Collections.synchronizedMap(HashMap())
    private val isBeingDestroyed = AtomicBoolean(false)
    val isEmpty: Boolean
        get() = _keys.isEmpty()

    @Volatile
    private var connectionListener: TopicListener? = null

    fun registerSubclient(subclient: BaseSubclient) {
        log.debug(
            "Adding {} for key '{}' in topic '{}'",
            subclient.javaClass.simpleName,
            subclient.key,
            subclient.topic
        )
        check(subclient.topic == topic) {
            "Attempting to register subclient with topic '${subclient.topic}' in TopicMetadata of '$topic'"
        }
        val metadata = getOrCreateKeyMetadata(subclient.key)
        when (subclient) {
            is ConsumerSubclient<*> -> {
                if (metadata.consumers.isEmpty() && connectionListener == null) {
                    log.debug("Creating new connection listener for topic '{}'", subclient.topic)
                    // New consumer - create a new connection listener for this topic
                    val listener = TopicListener { topic, key, value, headers ->
                        onTopicMessage(topic, key, value, headers)
                    }
                    connection.on(subclient.topic, listener)
                    connectionListener = listener
                }
                metadata.consumers.add(subclient)
            }

            is ProducerSubclient<*> -> {
                metadata.producers.add(subclient)
            }
        }
    }

    fun deregisterSubclient(subclient: BaseSubclient) {
        log.debug(
            "Removing {} for key '{}' in topic '{}'",
            subclient.javaClass.simpleName,
            subclient.key,
            subclient.topic
        )
        val metadata = getExistingKeyMetadata(subclient.key)
        metadata?.let {
            when (subclient) {
                is ConsumerSubclient<*> -> it.consumers.remove(subclient)
                is ProducerSubclient<*> -> it.producers.remove(subclient)
            }
            maybeCleanupKeyMetadata(it)
        }
    }

    private fun maybeCleanupKeyMetadata(keyMetadata: KeyMetadata) {
        if (keyMetadata.isEmpty) {
            _keys.remove(keyMetadata.key)
        }
        if (this.isEmpty) {
            connectionListener?.let {
                log.debug("Removing connection listener for topic '{}' after key cleanup", topic)
                connection.off(topic, it)
                connectionListener = null
            }
        }
    }

    private fun getOrCreateKeyMetadata(key: String): KeyMetadata {
        return _keys.computeIfAbsent(key) {
            KeyMetadata(key)
        }
    }

    private fun getExistingKeyMetadata(key: String): KeyMetadata? {
        return _keys[key]
    }

    private fun onTopicMessage(
        topic: String,
        key: String,
        value: String,
        headers: BrokerMessageHeaders,
    ) {
        val metadata = getExistingKeyMetadata(key) ?: return
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

    fun destroy() {
        if (!isBeingDestroyed.compareAndSet(false, true)) {
            return
        }
        while (_keys.isNotEmpty()) {
            val key = _keys.keys.first()
            _keys[key]?.destroy()
            _keys.remove(key)
        }
        connectionListener?.let {
            log.debug("Removing connection listener for topic '{}' in destroy()", topic)
            connection.off(topic, it)
            connectionListener = null
        }
    }

}

private class KeyMetadata(val key: String) {
    val producers: MutableSet<ProducerSubclient<*>> = Collections.synchronizedSet(HashSet())
    val consumers: MutableSet<ConsumerSubclient<*>> = Collections.synchronizedSet(HashSet())

    val isEmpty: Boolean
        get() = producers.isEmpty() && consumers.isEmpty()

    fun destroy() {
        producers.forEach(ProducerSubclient<*>::destroy)
        consumers.forEach(ConsumerSubclient<*>::destroy)
        producers.clear()
        consumers.clear()
    }
}

@PublishedApi
internal inline fun <reified T> isTypeNullable(): Boolean {
    return null is T || T::class.java == Unit::class.java || T::class.java == Void::class.java
}
