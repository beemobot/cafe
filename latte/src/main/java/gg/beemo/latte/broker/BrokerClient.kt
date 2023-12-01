package gg.beemo.latte.broker

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import gg.beemo.latte.logging.log
import gg.beemo.latte.util.SuspendingCountDownLatch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration

fun interface BrokerEventListener<T : Any> {
    suspend fun onMessage(msg: BrokerMessage<T>)
}

fun interface BrokerMessageListener<T : Any> {
    suspend fun onMessage(clusterId: String, msg: BrokerMessage<T>)
}

private class TopicMetadata(
    val topic: String,
    val keys: MutableMap<String, TopicKeyClient<out Any>>
)

class RpcClient<RequestType: Any, ResponseType: Any>

class TopicKeyClient<T : Any>(
    private val connection: BrokerConnection,
    private val client: BrokerClient,
    val topic: String,
    val key: String,
    val type: Class<T>,
) {

    private val adapter: JsonAdapter<T> = moshi.adapter(type).nullSafe()
    private val listeners: MutableList<BrokerEventListener<T>> = CopyOnWriteArrayList()

    suspend fun send(
        topic: String,
        key: String,
        obj: T?,
        headers: BaseBrokerMessageHeaders = this.connection.createHeaders(),
    ): String {
        return connection.send(topic, key, stringify(obj), headers)
    }

    private fun parse(json: String): T? {
        return adapter.fromJson(json)
    }

    private fun stringify(obj: T?): String {
        return adapter.toJson(obj)
    }

    companion object {
        // TODO Investigate if it's a good idea to add a global custom type adapter
        //  to serialize large Longs as Strings for easier JS compatibility.
        //  https://github.com/square/moshi#custom-type-adapters
        private val moshi = Moshi.Builder().build()
    }

}

abstract class BrokerClient(
    protected val connection: BrokerConnection,
) {

    private val topics: MutableMap<String, TopicMetadata> = Collections.synchronizedMap(HashMap())

    init {
        log.debug("Initializing Broker Client with topics '{}' for objects of type {}", topics, type.name)
        connection.on(topics, ::onTopicMessage)
    }

    protected suspend inline fun <reified T : Any> sendRpcRequest(
        topic: String,
        key: String,
        obj: T?,
        timeout: Duration = Duration.INFINITE,
    ): BrokerMessage<T> {
        return sendRpcRequest(topic, key, T::class.java, obj, timeout)
    }

    protected suspend fun <T : Any> sendRpcRequest(
        topic: String,
        key: String,
        type: Class<T>,
        obj: T?,
        timeout: Duration = Duration.INFINITE,
    ): BrokerMessage<T> {
        TODO()
    }

    // TODO Rename this to something RPC-related
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
