package gg.beemo.latte.broker

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import gg.beemo.latte.logging.log
import gg.beemo.latte.util.SuspendingCountDownLatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun interface BrokerEventListener<T : Any> {
    suspend fun onMessage(msg: BrokerMessage<T>)
}

fun interface BrokerMessageListener<T : Any> {
    suspend fun onMessage(clusterId: String, msg: BrokerMessage<T>)
}


sealed class BaseClient<OutgoingT : Any, IncomingT : Any>(
    protected val connection: BrokerConnection,
    protected val client: BrokerClient,
    val topic: String,
    val key: String,
    protected val outgoingType: Class<OutgoingT>?,
    protected val incomingType: Class<IncomingT>?,
    callback: (suspend CoroutineScope.(IncomingT) -> OutgoingT)?,
) {

    // TODO Should this class still exist?

    companion object {
        // TODO Investigate if it's a good idea to add a global custom type adapter
        //  to serialize large Longs as Strings for easier JS compatibility.
        //  https://github.com/square/moshi#custom-type-adapters
        @JvmStatic
        protected val moshi: Moshi = Moshi.Builder().build()
    }

}


class ProducerClient<OutgoingT : Any>(
    connection: BrokerConnection,
    client: BrokerClient,
    topic: String,
    key: String,
    outgoingType: Class<OutgoingT>,
) : BaseClient<OutgoingT, Unit>(
    connection,
    client,
    topic,
    key,
    outgoingType,
    null,
    null,
) {

    private val adapter: JsonAdapter<OutgoingT?> = moshi.adapter(outgoingType).nullSafe()

    suspend fun send(
        data: OutgoingT,
        services: Set<String> = emptySet(),
        instances: Set<String> = emptySet(),
    ) {
        val strigifiedData = stringifyOutgoing(data)
        val headers = connection.createHeaders(services, instances)
        connection.send(topic, key, strigifiedData, headers)
    }

    private fun stringifyOutgoing(data: OutgoingT?): String {
        return adapter.toJson(data)
    }

}

class ConsumerClient<IncomingT : Any>(
    connection: BrokerConnection,
    client: BrokerClient,
    topic: String,
    key: String,
    incomingType: Class<IncomingT>,
    callback: suspend CoroutineScope.(IncomingT) -> Unit,
) : BaseClient<Unit, IncomingT>(
    connection,
    client,
    topic,
    key,
    null,
    incomingType,
    callback,
) {

    private val adapter: JsonAdapter<IncomingT?> = moshi.adapter(incomingType).nullSafe()

    private fun parseIncoming(json: String): IncomingT? {
        return adapter.fromJson(json)
    }

}


class RpcClient<OutgoingT : Any, IncomingT : Any>(
    connection: BrokerConnection,
    client: BrokerClient,
    topic: String,
    key: String,
    outgoingType: Class<OutgoingT>,
    incomingType: Class<IncomingT>,
    callback: suspend CoroutineScope.(IncomingT) -> OutgoingT,
) : BaseClient<OutgoingT, IncomingT>(
    connection,
    client,
    topic,
    key,
    outgoingType,
    incomingType,
    callback,
) {

    private val producer = ProducerClient(connection, client, topic, key, outgoingType)
    private val consumer = ConsumerClient(connection, client, topic, key, incomingType) {
        TODO()
    }

    suspend fun call(
        request: OutgoingT,
        services: Set<String> = emptySet(),
        instances: Set<String> = emptySet(),
        timeout: Duration = 10.seconds,
    ): IncomingT {
        producer.send(request, services, instances)
        TODO()
    }

}


private class TopicMetadata(
    val topic: String,
    val keys: MutableMap<String, TopicKeyClient<out Any>>
)

abstract class BrokerClient(
    @PublishedApi
    internal val connection: BrokerConnection,
) {

    inline fun <reified T : Any> consumer(
        topic: String,
        key: String,
        noinline callback: suspend CoroutineScope.(T) -> Unit,
    ): ConsumerClient<T> {
        return ConsumerClient(connection, this, topic, key, T::class.java, callback)
    }

    inline fun <reified T : Any> producer(
        topic: String,
        key: String,
    ): ProducerClient<T> {
        return ProducerClient(connection, this, topic, key, T::class.java)
    }

    inline fun <reified IncomingT : Any, reified OutgoingT : Any> rpc(
        topic: String,
        key: String,
        noinline callback: suspend CoroutineScope.(IncomingT) -> OutgoingT,
    ): RpcClient<IncomingT, OutgoingT> {
        return RpcClient(connection, this, topic, key, IncomingT::class.java, OutgoingT::class.java, callback)
    }

    internal suspend fun send(
        topic: String,
        key: String,
        data: String,
        headers: BaseBrokerMessageHeaders = this.connection.createHeaders(),
    ): String {
        return connection.send(topic, key, data, headers)
    }

    private val topics: MutableMap<String, TopicMetadata> = Collections.synchronizedMap(HashMap())

    init {
        log.debug("Initializing Broker Client with topics '{}' for objects of type {}", topics, type.name)
        connection.on(topics, ::onTopicMessage)
    }

    protected suspend fun <T : Any> sendClusterRequest(
        topic: String,
        key: String,
        obj: T?,
        timeout: Duration = Duration.INFINITE,
        targetClusters: Set<String> = emptySet(),
        expectedResponses: Int? = null,
        messageCallback: BrokerMessageListener<T>? = null,
    ): Pair<Map<String, BrokerMessage<T>>, Boolean> {
        val responseKey = key.toResponseKey()

        val responses = ConcurrentHashMap<String, BrokerMessage<T>>()
        val latch = SuspendingCountDownLatch(expectedResponses ?: targetClusters.size)
        val requestId: AtomicReference<String> = AtomicReference("")

        val cb = BrokerEventListener { msg ->
            coroutineScope {
                if (msg.headers.requestId != requestId.get()) {
                    return@coroutineScope
                }
                launch {
                    try {
                        messageCallback?.onMessage(msg.headers.sourceCluster, msg)
                    } catch (t: Throwable) {
                        log.error("Uncaught error in sendClusterRequest message callback", t)
                    }
                }
                responses[msg.headers.sourceCluster] = msg
                latch.countDown()
            }
        }

        on(topic, responseKey, cb)
        val timeoutReached: Boolean
        try {
            val headers = this.connection.createHeaders(targetClusters)
            requestId.set(headers.requestId)
            send(topic, key, obj, headers)

            timeoutReached = !latch.await(timeout)
        } finally {
            off(topic, responseKey, cb)
        }

        return Pair(responses, timeoutReached)
    }

    protected inline fun <reified T : Any> on(topic: String, key: String, cb: BrokerEventListener<T>) {
        on(topic, key, T::class.java, cb)
    }

    protected fun <T : Any> on(topic: String, key: String, type: Class<T>, cb: BrokerEventListener<T>) {
        getTopicKeyMetadata(topic, key, type).listeners.add(cb)
    }

    protected fun <T : Any> off(topic: String, key: String, cb: BrokerEventListener<T>) {
        getTopicKeyMetadata<T>(topic, key)?.listeners?.remove(cb)
    }

    internal suspend fun <T : Any> respond(
        msg: BrokerMessage<T>,
        data: T?,
    ) {
        val newHeaders = this.connection.createHeaders(
            setOf(msg.headers.sourceCluster),
            msg.headers.requestId,
        )
        send(
            msg.topic,
            msg.key.toResponseKey(),
            data,
            newHeaders,
        )
    }

    private suspend fun <T : Any> onTopicMessage(
        topic: String,
        key: String,
        value: String,
        headers: BaseBrokerMessageHeaders,
    ) = coroutineScope {
        val metadata = getTopicKeyMetadata<T>(topic, key)
        requireNotNull(metadata) { "Can't find metadata for key '$key' in topic '$topic'" }
        val obj = metadata.parse(value)
        val msg = BrokerMessage(this@BrokerClient, topic, key, obj, headers)
        for (listener in metadata.listeners) {
            launch {
                try {
                    listener.onMessage(msg)
                } catch (t: Throwable) {
                    log.error("Uncaught error in BrokerClient listener for key '$key' in topic '$topic'", t)
                }
            }
        }
    }

    private fun <T : Any> getTopicKeyMetadata(topic: String, key: String): TopicKeyClient<T>? {
        val topicData = topics[topic] ?: return null
        return topicData.keys[key]
    }

    private fun <T : Any> getTopicKeyMetadata(topic: String, key: String, type: Class<T>): TopicKeyClient<T> {
        val topicData = topics.computeIfAbsent(topic) {
            TopicMetadata(topic, Collections.synchronizedMap(HashMap()))
        }
        val keyData = topicData.keys.computeIfAbsent(key) {
            val adapter = moshi.adapter(type).nullSafe()
            TopicKeyClient(topic, key, type, adapter, CopyOnWriteArrayList())
        }
        require(keyData.type == type) {
            "Key '$key' in topic '$topic' is already registered with type ${keyData.type.name}, " +
                    "attempting to process it with type ${type.name} instead"
        }
        @Suppress("UNCHECKED_CAST") // Safe because of the above verification
        return keyData as TopicKeyClient<T>
    }

}

internal fun String.toResponseKey(): String = "${this}-response"
