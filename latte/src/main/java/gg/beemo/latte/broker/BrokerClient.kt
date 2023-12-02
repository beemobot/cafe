package gg.beemo.latte.broker

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import gg.beemo.latte.logging.log
import gg.beemo.latte.util.SuspendingCountDownLatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


fun interface BrokerMessageListener<T : Any> {
    suspend fun onMessage(clusterId: String, msg: BrokerMessage<T>)
}


sealed class BaseSubclient<RequestT : Any, ResponseT : Any>(
    protected val connection: BrokerConnection,
    protected val client: BrokerClient,
    val topic: String,
    val key: String,
) {

    internal abstract fun destroy()

    companion object {
        // TODO Investigate if it's a good idea to add a global custom type adapter
        //  to serialize large Longs as Strings for easier JS compatibility.
        //  https://github.com/square/moshi#custom-type-adapters
        @JvmStatic
        protected val moshi: Moshi = Moshi.Builder().build()
    }

}

class ProducerSubclient<T : Any>(
    connection: BrokerConnection,
    client: BrokerClient,
    topic: String,
    key: String,
    requestType: Class<T>,
) : BaseSubclient<T, Unit>(
    connection,
    client,
    topic,
    key,
) {

    private val adapter: JsonAdapter<T?> = moshi.adapter(requestType).nullSafe()

    override fun destroy() {
        TODO()
    }

    suspend fun send(
        data: T,
        services: Set<String> = emptySet(),
        instances: Set<String> = emptySet(),
    ) {
        val strigifiedData = stringifyOutgoing(data)
        val headers = connection.createHeaders(services, instances)
        connection.send(topic, key, strigifiedData, headers)
    }

    private fun stringifyOutgoing(data: T?): String {
        return adapter.toJson(data)
    }

}

class ConsumerSubclient<T : Any>(
    connection: BrokerConnection,
    client: BrokerClient,
    topic: String,
    key: String,
    incomingType: Class<T>,
    private val callback: suspend CoroutineScope.(T) -> Unit,
) : BaseSubclient<Unit, T>(
    connection,
    client,
    topic,
    key,
) {

    private val adapter: JsonAdapter<T?> = moshi.adapter(incomingType).nullSafe()

    override fun destroy() {
        TODO()
    }

    internal suspend fun onIncomingMessage(
        value: String,
        headers: BaseBrokerMessageHeaders,
    ) = coroutineScope {
        val data = parseIncoming(value)
        // TODO Better nullability support - let the caller decide if nulls are allowed
        callback(data!!)
    }

    private fun parseIncoming(json: String): T? {
        return adapter.fromJson(json)
    }

}

class RpcClient<RequestT : Any, ResponseT : Any>(
    private val connection: BrokerConnection,
    private val client: BrokerClient,
    private val topic: String,
    private val key: String,
    private val requestType: Class<RequestT>,
    private val responseType: Class<ResponseT>,
    private val callback: suspend CoroutineScope.(RequestT) -> ResponseT,
) {

    // TODO There are two producers, one for sending requests and one for sending responses.
    //  The response producer will be dynamically created for each request?
    private val requestProducer = client.producer<RequestT>(topic, key, requestType)

    // TODO Same for consumers. One will receive incoming requests, one will receive incoming responses after sending a request.
    private val requestConsumer = client.consumer<RequestT>(topic, key, requestType) {
        TODO()
    }

    suspend fun call(
        request: RequestT,
        services: Set<String> = emptySet(),
        instances: Set<String> = emptySet(),
        timeout: Duration = 10.seconds,
    ): ResponseT {
        requestProducer.send(request, services, instances)
        TODO()
    }

    suspend fun stream(
        request: RequestT,
        services: Set<String> = emptySet(),
        instances: Set<String> = emptySet(),
        timeout: Duration = 10.seconds,
        maxResponses: Int? = null,
    ): Flow<ResponseT> = callbackFlow {
        val responseConsumer = client.consumer<ResponseT>(topic, key, responseType) {
            // TODO Cound max responses and timeout and stuff
            send(it)
        }
        awaitClose {
            responseConsumer.destroy()
        }
        TODO()
    }

}

private class TopicMetadata(
    val topic: String,
    val keys: MutableMap<String, KeyMetadata>
)

private class KeyMetadata(
    val topic: String,
    val key: String,
    val producers: MutableSet<ProducerSubclient<*>>,
    val consumers: MutableSet<ConsumerSubclient<*>>,
)

abstract class BrokerClient(
    @PublishedApi
    internal val connection: BrokerConnection,
) {

    private val topics: MutableMap<String, TopicMetadata> = Collections.synchronizedMap(HashMap())

    init {
        log.debug("Initializing Broker Client with topics '{}' for objects of type {}", topics, type.name)
        connection.on(topics, ::onTopicMessage)
    }

    inline fun <reified T : Any> consumer(
        topic: String,
        key: String,
        noinline callback: suspend CoroutineScope.(T) -> Unit,
    ): ConsumerSubclient<T> {
        return consumer(topic, key, T::class.java, callback)
    }

    fun <T : Any> consumer(
        topic: String,
        key: String,
        type: Class<T>,
        callback: suspend CoroutineScope.(T) -> Unit,
    ): ConsumerSubclient<T> {
        return ConsumerSubclient(connection, this, topic, key, type, callback).also {
            registerConsumer(it)
        }
    }

    inline fun <reified T : Any> producer(
        topic: String,
        key: String,
    ): ProducerSubclient<T> {
        return producer(topic, key, T::class.java)
    }

    fun <T : Any> producer(
        topic: String,
        key: String,
        type: Class<T>,
    ): ProducerSubclient<T> {
        return ProducerSubclient(connection, this, topic, key, type).also {
            registerProducer(it)
        }
    }

    inline fun <reified RequestT : Any, reified ResponseT : Any> rpc(
        topic: String,
        key: String,
        noinline callback: suspend CoroutineScope.(RequestT) -> ResponseT,
    ): RpcClient<RequestT, ResponseT> {
        return RpcClient(connection, this, topic, key, RequestT::class.java, ResponseT::class.java, callback)
    }

    @PublishedApi
    internal fun registerProducer(producer: ProducerSubclient<*>) {
        val metadata = getOrCreateKeyMetadata(producer.topic, producer.key)
        metadata.producers.add(producer)
    }

    @PublishedApi
    internal fun registerConsumer(consumer: ConsumerSubclient<*>) {
        val metadata = getOrCreateKeyMetadata(consumer.topic, consumer.key)
        metadata.consumers.add(consumer)
    }

    private fun getOrCreateKeyMetadata(topic: String, key: String): KeyMetadata {
        val topicData = topics.computeIfAbsent(topic) {
            // TODO Tell connection to create the topic, if applicable
            TopicMetadata(topic, Collections.synchronizedMap(HashMap()))
        }
        val keyData = topicData.keys.computeIfAbsent(key) {
            KeyMetadata(topic, key, Collections.synchronizedSet(HashSet()), Collections.synchronizedSet(HashSet()))
        }
        return keyData
    }

    internal fun createResponseTopic(topic: String): String = "$topic.responses"
    internal fun createResponseKey(key: String): String = "$key.response"

    // ---------- Old code ----------

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
        val metadata = getOrCreateKeyMetadata<T>(topic, key)
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

}
