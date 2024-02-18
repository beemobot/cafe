package gg.beemo.latte.broker

import gg.beemo.latte.logging.Log
import java.util.*

fun interface TopicListener {
    fun onMessage(topic: String, key: String, value: String, headers: BrokerMessageHeaders)
}

typealias MessageId = String

abstract class BrokerConnection {

    abstract val serviceName: String
    abstract val instanceId: String
    abstract val supportsTopicHotSwap: Boolean

    protected val topicListeners: MutableMap<String, MutableSet<TopicListener>> = Collections.synchronizedMap(HashMap())

    private val log by Log

    abstract suspend fun start()
    open fun destroy() {
        log.debug("Destroying BrokerConnection")
        topicListeners.clear()
    }

    internal abstract suspend fun abstractSend(
        topic: String,
        key: String,
        value: String,
        headers: BrokerMessageHeaders,
    ): MessageId

    internal suspend fun send(
        topic: String,
        key: String,
        value: String,
        headers: BrokerMessageHeaders,
    ): MessageId {
        log.trace(
            "Sending message {} with key '{}' in topic '{}' with value: {}",
            headers.messageId,
            key,
            topic,
            value,
        )
        return abstractSend(topic, key, value, headers)
    }

    protected abstract fun createTopic(topic: String)
    protected abstract fun removeTopic(topic: String)

    internal fun on(topic: String, cb: TopicListener) {
        topicListeners.computeIfAbsent(topic) {
            log.debug("Creating new topic '{}'", topic)
            createTopic(topic)
            Collections.synchronizedSet(HashSet())
        }.add(cb)
    }

    internal fun off(topic: String, cb: TopicListener) {
        topicListeners.computeIfPresent(topic) { _, listeners ->
            listeners.remove(cb)
            if (listeners.size == 0) {
                log.debug("Removing topic '{}'", topic)
                removeTopic(topic)
                null
            } else {
                listeners
            }
        }
    }

    // To be called by implementers
    protected fun dispatchIncomingMessage(
        topic: String,
        key: String,
        value: String,
        headers: BrokerMessageHeaders
    ) {
        if (
            (headers.targetServices.isNotEmpty() && serviceName !in headers.targetServices) ||
            (headers.targetInstances.isNotEmpty() && instanceId !in headers.targetInstances)
        ) {
            // If there is a target cluster restriction and this message wasn't meant for us,
            // discard it immediately without notifying any listeners.
            return
        }
        if (headers.sourceInstance == instanceId && headers.sourceService == serviceName) {
            // If this message was sent by ourselves, discard it too, as we already dispatch events
            // to our listeners in `send()` to avoid the round trip through an external service.
            return
        }
        invokeLocalCallbacks(topic, key, value, headers)
    }

    protected fun shouldDispatchExternallyAfterShortCircuit(
        topic: String,
        key: String,
        value: String,
        headers: BrokerMessageHeaders
    ): Boolean {
        val targetServices = headers.targetServices
        val targetInstances = headers.targetInstances
        val isThisConnectionTargeted =
            (targetServices.isEmpty() || serviceName in targetServices) &&
                    (targetInstances.isEmpty() || instanceId in targetInstances)

        // If the message is meant for ourselves (amongst other clusters),
        // immediately dispatch it to the listeners.
        if (isThisConnectionTargeted) {
            invokeLocalCallbacks(topic, key, value, headers)
        }

        // Return whether implementers should dispatch this message to external services
        return (
                // For all services/instances
                targetServices.isEmpty() || targetInstances.isEmpty() ||
                        // Not for us, so it must be for somebody else
                        !isThisConnectionTargeted ||
                        // For us, so check if it is also for someone else
                        targetServices.size > 1 || targetInstances.size > 1
                )
    }

    private fun invokeLocalCallbacks(topic: String, key: String, value: String, headers: BrokerMessageHeaders) {
        log.trace(
            "Dispatching message {} with key '{}' in topic '{}' to local listeners with value: {}",
            headers.messageId,
            key,
            topic,
            value,
        )
        val listeners = topicListeners[topic] ?: return
        for (listener in listeners) {
            try {
                listener.onMessage(topic, key, value, headers)
            } catch (e: Exception) {
                log.error("Uncaught error in BrokerConnection listener for key '$key' in topic '$topic'", e)
            }
        }
    }

}
