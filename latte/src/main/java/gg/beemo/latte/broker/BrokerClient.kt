package gg.beemo.latte.broker

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

open class BrokerClient<T : Any>(
    private val connection: BrokerConnection,
    type: Class<T>,
    private val topicName: String
) {

    val clusterId: String
        get() = connection.clusterId

    val clientId: String
        get() = connection.clientId

    private val moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(type).nullSafe()
    private val keyListeners = Collections.synchronizedMap(HashMap<String, MutableSet<BrokerEventListener<T>>>())

    init {
        log.debug("Initializing Broker Client with topic '$topicName' for objects of type ${type.name}")
        connection.on(topicName, ::onTopicMessage)
    }

    protected suspend fun send(
        key: String,
        obj: T?,
        headers: BaseBrokerMessageHeaders = this.connection.createHeaders(),
        blocking: Boolean = true,
    ): String {
        return connection.send(topicName, key, stringify(obj), headers, blocking)
    }

    protected suspend fun sendClusterRequest(
        key: String,
        obj: T?,
        timeout: Duration = Duration.ZERO,
        targetClusters: Set<String> = emptySet(),
        expectedResponses: Int? = null,
        blocking: Boolean = true,
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

        on(responseKey, cb)
        var timeoutReached = false
        try {
            val headers = this.connection.createHeaders(targetClusters)
            requestId.set(headers.requestId)
            send(key, obj, headers, blocking)

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

    protected fun on(key: String, cb: BrokerEventListener<T>) {
        val listeners = keyListeners.computeIfAbsent(key) {
            CopyOnWriteArraySet()
        }
        listeners.add(cb)
    }

    protected fun off(key: String, cb: BrokerEventListener<T>) {
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
        msg: BrokerMessage<T>,
        data: T?,
        blocking: Boolean = true,
    ) {
        val newHeaders = this.connection.createHeaders(
            setOf(msg.headers.sourceCluster),
            msg.headers.requestId,
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

}

internal fun String.toResponseKey(): String = "${this}-response"
