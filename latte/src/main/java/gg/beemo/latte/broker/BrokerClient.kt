package gg.beemo.latte.broker

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import gg.beemo.latte.logging.log
import gg.beemo.latte.util.SuspendingCountDownLatch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration

fun interface BrokerEventListener<T : Any> {
    suspend fun onMessage(msg: BrokerMessage<T>)
}

fun interface BrokerMessageListener<T : Any> {
    suspend fun onMessage(clusterId: String, msg: BrokerMessage<T>)
}

private class TopicData<T : Any>(
    val type: Class<T>,
    val adapter: JsonAdapter<T>,
    val listeners: MutableMap<String, BrokerEventListener<T>>,
) {

    fun parse(json: String): T? {
        return adapter.fromJson(json)
    }

    fun stringify(obj: T?): String {
        return adapter.toJson(obj)
    }

}

abstract class BrokerClient(
    protected val connection: BrokerConnection,
    // TODO initialize topics when registering event listeners instead?
    initialTopics: Map<String, Class<Any>> = emptyMap(),
) {

    companion object {
        // TODO Investigate if it's a good idea to add a global custom type adapter
        //  to serialize large Longs as Strings for easier JS compatibility.
        //  https://github.com/square/moshi#custom-type-adapters
        private val moshi = Moshi.Builder().build()
    }

    private val topics: MutableMap<String, TopicData<*>> = Collections.synchronizedMap(HashMap())

    init {
        log.debug("Initializing Broker Client with topics '{}' for objects of type {}", topics, type.name)
        connection.on(topics, ::onTopicMessage)
    }

    protected suspend fun <T : Any> send(
        topic: String,
        key: String,
        obj: T?,
        headers: BaseBrokerMessageHeaders = this.connection.createHeaders(),
    ): String {
        val topicData = getTopicData(topic, obj)
        requireNotNull(topicData) { "Attempting to send to unregistered topic $topic" }
        return connection.send(topic, key, topicData.stringify(obj), headers)
    }

    protected suspend fun <T : Any> sendClusterRequest(
        topic: String,
        key: String,
        obj: T?,
        timeout: Duration = Duration.ZERO,
        targetClusters: Set<String> = emptySet(),
        expectedResponses: Int? = null,
        messageCallback: BrokerMessageListener<T>? = null,
    ): Pair<Map<String, BrokerMessage<T>>, Boolean> {
        val responseKey = key.toResponseKey()

        val responses = mutableMapOf<String, BrokerMessage<T>>()
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
        var timeoutReached = false
        try {
            val headers = this.connection.createHeaders(targetClusters)
            requestId.set(headers.requestId)
            send(topic, key, obj, headers)

            if (timeout <= Duration.ZERO) {
                latch.await()
            } else {
                timeoutReached = !latch.await(timeout)
            }
        } finally {
            off(topic, responseKey, cb)
        }

        return Pair(responses, timeoutReached)
    }

    protected inline fun <reified T : Any> on(topic: String, key: String, cb: BrokerEventListener<T>) {
        on(topic, key, T::class.java, cb)
    }

    protected fun <T : Any> on(topic: String, key: String, type: Class<T>, cb: BrokerEventListener<T>) {
        val topicData = topics.computeIfAbsent(topic) {
            val adapter = moshi.adapter(type).nullSafe()
            TopicData(type, adapter, Collections.synchronizedMap(HashMap()))
        }
        require(topicData.type == type) {
            "Topic '$topic' is already registered with type ${topicData.type.name}, " +
                    "attempting to re-register it with type ${type.name}"
        }
        @Suppress("UNCHECKED_CAST") // Safe because of the above verification
        (topicData as TopicData<T>).listeners[key] = cb
    }

    protected fun <T : Any> off(topic: String, key: String, cb: BrokerEventListener<T>) {
        topics.computeIfPresent(topic) { _, topicData ->
            topicData.listeners.remove(key, cb)
            if (topicData.listeners.isEmpty()) {
                null
            } else {
                topicData
            }
        }
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
            msg.key.toResponseKey(),
            data,
            newHeaders,
        )
    }

    private suspend fun onTopicMessage(key: String, value: String, headers: BaseBrokerMessageHeaders) = coroutineScope {
        val obj = parse(value)
        val msg = BrokerMessage(this@BrokerClient, key, obj, headers)
        val listeners = keyListeners[key]
        for (listener in listeners ?: return@coroutineScope) {
            launch {
                try {
                    listener.onMessage(msg)
                } catch (t: Throwable) {
                    log.error("Uncaught error in BrokerClient listener", t)
                }
            }
        }
    }

    private fun <T:Any> getTopicData(topic: String, obj: T?): TopicData<T>? {
        val topicData = topics[topic] ?: return null
        if (obj != null) {
            require(topicData.type == obj::class.java) {
                "Topic '$topic' is already registered with type ${topicData.type.name}, " +
                        "attempting to send with type ${obj::class.java.name}"
            }
        }
        @Suppress("UNCHECKED_CAST") // Safe because of the above verification
        return topicData as TopicData<T>
    }

}

internal fun String.toResponseKey(): String = "${this}-response"
