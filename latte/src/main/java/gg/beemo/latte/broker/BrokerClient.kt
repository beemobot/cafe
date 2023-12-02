package gg.beemo.latte.broker

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import gg.beemo.latte.logging.log
import gg.beemo.latte.util.SuspendingCountDownLatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


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
    ): MessageId {
        val strigifiedData = stringifyOutgoing(data)
        val headers = connection.createHeaders(services, instances)
        return connection.send(topic, key, strigifiedData, headers)
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
        val message = BrokerMessage(client, topic, key, data, headers)
        // TODO Better nullability support - let the caller decide if nulls are allowed
        // TODO Return the full BrokerMessage
        callback(message.value!!)
    }

    private fun parseIncoming(json: String): T? {
        return adapter.fromJson(json)
    }

}

class RpcClient<RequestT : Any, ResponseT : Any>(
    private val client: BrokerClient,
    private val topic: String,
    private val key: String,
    requestType: Class<RequestT>,
    private val responseType: Class<ResponseT>,
    private val callback: suspend CoroutineScope.(RequestT) -> ResponseT,
) {

    private val requestProducer = client.producer(topic, key, requestType)

    private val requestConsumer = client.consumer(topic, key, requestType) {
        val result = callback(it)
        val responseProducer = client.producer(
            client.createResponseTopic(topic),
            client.createResponseKey(key),
            responseType,
        )
        responseProducer.send(result) // TODO Send to source service/instance
        responseProducer.destroy()
    }

    suspend fun call(
        request: RequestT,
        services: Set<String> = emptySet(),
        instances: Set<String> = emptySet(),
        timeout: Duration = 10.seconds,
    ): ResponseT {
        return stream(request, services, instances, timeout, 1).single()
    }

    suspend fun stream(
        request: RequestT,
        services: Set<String> = emptySet(),
        instances: Set<String> = emptySet(),
        timeout: Duration = 10.seconds,
        maxResponses: Int? = null,
    ): Flow<ResponseT> {
        require(timeout.isFinite() || maxResponses != null) {
            "Must specify either a timeout or a max number of responses"
        }
        if (maxResponses != null) {
            require(maxResponses > 0) { "maxResponses must be at least 1" }
        }
        return callbackFlow {
            val responseCounter = AtomicInteger(0)
            val timeoutLatch = maxResponses?.let { SuspendingCountDownLatch(it) }
            val messageId = AtomicReference<String?>(null)

            val responseConsumer = client.consumer(
                client.createResponseTopic(topic),
                client.createResponseKey(key),
                responseType,
            ) {
                // TODO great now I don't have access to the headers with the inReplyTo to validate it
                send(it)
                timeoutLatch?.countDown()
                val count = responseCounter.incrementAndGet()
                if (maxResponses != null && count >= maxResponses) {
                    close()
                }
            }

            messageId.set(requestProducer.send(request, services, instances))

            invokeOnClose {
                responseConsumer.destroy()
            }

            if (timeoutLatch != null) {
                timeoutLatch.awaitThrowing(timeout)
            } else {
                delay(timeout)
            }
            close()
        }

    }

    fun destroy() {
        requestProducer.destroy()
        requestConsumer.destroy()
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
        return RpcClient(this, topic, key, RequestT::class.java, ResponseT::class.java, callback)
    }

    private fun registerProducer(producer: ProducerSubclient<*>) {
        val metadata = getOrCreateKeyMetadata(producer.topic, producer.key)
        metadata.producers.add(producer)
    }

    private fun registerConsumer(consumer: ConsumerSubclient<*>) {
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
