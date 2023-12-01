package gg.beemo.latte.broker

import gg.beemo.latte.logging.log
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.CopyOnWriteArraySet

fun interface TopicListener {
    suspend fun onMessage(topic: String, key: String, value: String, headers: BaseBrokerMessageHeaders)
}

abstract class BrokerConnection {

    abstract val clientId: String
    abstract val clusterId: String

    protected val topicListeners: MutableMap<String, MutableSet<TopicListener>> = Collections.synchronizedMap(HashMap())

    private val eventDispatcherScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val eventDispatcherErrorHandler = CoroutineExceptionHandler { _, t ->
        log.error("Uncaught error in BrokerConnection event handler", t)
    }

    abstract fun start()
    open fun destroy() {
        topicListeners.clear()
        eventDispatcherScope.cancel()
    }

    abstract suspend fun send(
        topic: String,
        key: String,
        value: String,
        headers: BaseBrokerMessageHeaders,
    ): String

    abstract fun createHeaders(
        targetServices: Set<String> = emptySet(),
        targetInstances: Set<String> = emptySet(),
        inReplyTo: String? = null,
    ): BaseBrokerMessageHeaders

    open fun on(topic: String, cb: TopicListener) {
        topicListeners.computeIfAbsent(topic) { CopyOnWriteArraySet() }.add(cb)
    }

    open fun off(topic: String, cb: TopicListener) {
        topicListeners.computeIfPresent(topic) { _, listeners ->
            listeners.remove(cb)
            if (listeners.size == 0) {
                null
            } else {
                listeners
            }
        }
    }

    protected fun shouldDispatchExternallyAfterShortCircuit(
        topic: String,
        key: String,
        value: String,
        headers: BaseBrokerMessageHeaders
    ): Boolean {
        val targets = headers.targetClusters
        val isLocalClusterInTargets = clusterId in targets

        // If the message is meant for ourselves (amongst other clusters),
        // immediately dispatch it to the listeners.
        if (targets.isEmpty() || isLocalClusterInTargets) {
            invokeCallbacks(topic, key, value, headers)
        }

        val isLocalClusterTheOnlyTarget = targets.size == 1 && isLocalClusterInTargets
        // Return whether implementers should dispatch this message to external services
        return targets.isEmpty() || !isLocalClusterTheOnlyTarget
    }

    protected fun handleIncomingMessage(
        topic: String,
        key: String,
        value: String,
        headers: BaseBrokerMessageHeaders
    ) {
        if (headers.targetClusters.isNotEmpty() && clusterId !in headers.targetClusters) {
            // If there is a target cluster restriction and this message wasn't meant for us,
            // discard it immediately without notifying any listeners.
            return
        }
        if (headers.sourceCluster == clusterId) {
            // If this message was sent by ourselves, discard it too, as we already dispatch events
            // to our listeners in `send()` to avoid the round trip through an external service.
            return
        }
        invokeCallbacks(topic, key, value, headers)
    }

    private fun invokeCallbacks(topic: String, key: String, value: String, headers: BaseBrokerMessageHeaders) {
        eventDispatcherScope.launch(eventDispatcherErrorHandler) {
            val listeners = topicListeners[topic]
            for (listener in listeners ?: return@launch) {
                try {
                    listener.onMessage(topic, key, value, headers)
                } catch (t: Throwable) {
                    log.error("Uncaught error in BrokerConnection listener", t)
                }
            }
        }
    }

}
