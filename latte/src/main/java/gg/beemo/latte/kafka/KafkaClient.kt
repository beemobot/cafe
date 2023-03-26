package gg.beemo.latte.kafka

import com.squareup.moshi.Moshi
import gg.beemo.latte.logging.log
import gg.beemo.latte.util.SuspendingCountDownLatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration

fun interface KafkaEventListener<T : Any> {
    suspend fun onMessage(msg: KafkaMessage<T>)
}

fun interface KafkaMessageListener<T : Any> {
    suspend fun onMessage(clusterId: Int, msg: KafkaMessage<T>)
}

open class KafkaClient<T : Any>(
    private val connection: KafkaConnection,
    type: Class<T>,
    private val topicName: String
) {

    val currentClusterId: Int
        get() = connection.currentClusterId

    val clientId: String
        get() = connection.clientId

    private val moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(type).nullSafe()
    private val keyListeners = Collections.synchronizedMap(HashMap<String, MutableSet<KafkaEventListener<T>>>())

    init {
        log.debug("Initializing Kafka Client with topic '$topicName' for objects of type ${type.name}")
        connection.on(topicName, ::onTopicRecord)
    }

    protected suspend fun send(
        key: String,
        obj: T?,
        headers: KafkaRecordHeaders = KafkaRecordHeaders(clientId, currentClusterId),
        blocking: Boolean = true,
    ): String {
        return connection.send(topicName, key, stringify(obj), headers, blocking)
    }

    protected suspend fun sendClusterRequest(
        key: String,
        obj: T?,
        timeout: Duration = Duration.ZERO,
        targetClusters: Set<Int> = emptySet(),
        expectedResponses: Int? = null,
        blocking: Boolean = true,
        messageCallback: KafkaMessageListener<T>? = null,
    ): Pair<Map<Int, KafkaMessage<T>>, Boolean> {
        val responseKey = key.toResponseKey()

        val responses = mutableMapOf<Int, KafkaMessage<T>>()
        val latch = SuspendingCountDownLatch(expectedResponses ?: targetClusters.size)
        val requestId: AtomicReference<String> = AtomicReference("")

        val cb = KafkaEventListener { msg ->
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

        on(responseKey, cb)
        var timeoutReached = false
        try {
            requestId.set(
                send(
                    key,
                    obj,
                    KafkaRecordHeaders(
                        clientId,
                        currentClusterId,
                        targetClusters,
                    ),
                    blocking,
                )
            )

            if (timeout <= Duration.ZERO) {
                latch.await()
            } else {
                timeoutReached = !latch.await(timeout)
            }
        } finally {
            off(responseKey, cb)
        }

        return Pair(responses, timeoutReached)
    }

    protected fun on(key: String, cb: KafkaEventListener<T>) {
        val listeners = keyListeners.computeIfAbsent(key) {
            CopyOnWriteArraySet()
        }
        listeners.add(cb)
    }

    protected fun off(key: String, cb: KafkaEventListener<T>) {
        keyListeners.computeIfPresent(key) { _, listeners ->
            listeners.remove(cb)
            if (listeners.size == 0) {
                null
            } else {
                listeners
            }
        }
    }

    internal suspend fun respond(
        msg: KafkaMessage<T>,
        data: T?,
        blocking: Boolean = true,
    ) {
        val newHeaders = KafkaRecordHeaders(
            clientId = clientId,
            sourceCluster = currentClusterId,
            targetClusters = setOf(msg.headers.sourceCluster),
            requestId = msg.headers.requestId,
        )
        send(
            msg.key.toResponseKey(),
            data,
            newHeaders,
            blocking,
        )
    }

    private fun parse(json: String): T? {
        return adapter.fromJson(json)
    }

    private fun stringify(obj: T?): String {
        return adapter.toJson(obj)
    }

    private suspend fun onTopicRecord(key: String, value: String, headers: KafkaRecordHeaders) = coroutineScope {
        val obj = parse(value)
        val msg = KafkaMessage(this@KafkaClient, key, obj, headers)
        val listeners = keyListeners[key]
        for (listener in listeners ?: return@coroutineScope) {
            launch(Dispatchers.Default) {
                try {
                    listener.onMessage(msg)
                } catch (t: Throwable) {
                    log.error("Uncaught error in KafkaClient listener", t)
                }
            }
        }
    }

}

internal fun String.toResponseKey(): String = "${this}-response"
