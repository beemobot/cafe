package gg.beemo.latte.broker

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import gg.beemo.latte.broker.rpc.RpcMessageHeaders
import gg.beemo.latte.logging.Log
import gg.beemo.latte.util.MoshiInstantAdapter
import gg.beemo.latte.util.MoshiJsLongAdapter
import gg.beemo.latte.util.MoshiUnitAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

data class BrokerClientOptions(
    val useSafeJsLongs: Boolean = false,
)

abstract class BaseSubclient(
    protected val connection: BrokerConnection,
    protected val client: BrokerClient,
    val topic: String,
    val key: String,
    protected val options: BrokerClientOptions,
) {

    private val log by Log
    private val isBeingDestroyed = AtomicBoolean(false)

    internal fun destroy() {
        if (isBeingDestroyed.compareAndSet(false, true)) {
            doDestroy()
        }
    }

    protected abstract fun doDestroy()

    protected fun <T> createMoshiAdapter(type: Class<T>): JsonAdapter<T?> {
        val mochi = if (options.useSafeJsLongs) safeJsMoshi else baseMoshi
        return mochi.adapter(type).nullSafe()
    }

    companion object {
        private val baseMoshi: Moshi = Moshi.Builder()
            .add(Unit::class.java, MoshiUnitAdapter())
            .add(Instant::class.java, MoshiInstantAdapter())
            .build()
        private val safeJsMoshi: Moshi = baseMoshi
            .newBuilder()
            .add(Long::class.java, MoshiJsLongAdapter())
            .build()
    }

}

class ProducerSubclient<T>(
    connection: BrokerConnection,
    client: BrokerClient,
    topic: String,
    key: String,
    options: BrokerClientOptions,
    requestType: Class<T>,
    private val isNullable: Boolean,
) : BaseSubclient(
    connection,
    client,
    topic,
    key,
    options,
) {

    private val log by Log
    private val adapter: JsonAdapter<T?> = createMoshiAdapter(requestType)

    override fun doDestroy() {
        client.deregisterSubclient(this)
    }

    suspend fun send(
        data: T,
        services: Set<String> = emptySet(),
        instances: Set<String> = emptySet(),
    ): MessageId {
        val msg = BrokerMessage(
            topic,
            key,
            data,
            BrokerMessageHeaders(
                connection,
                targetServices = services,
                targetInstances = instances,
            ),
        )
        @Suppress("UNCHECKED_CAST")
        return internalSend(msg as AbstractBrokerMessage<T?>)
    }

    internal suspend fun internalSend(msg: AbstractBrokerMessage<T?>, bypassNullCheck: Boolean = false): MessageId {
        if (!bypassNullCheck && !isNullable) {
            requireNotNull(msg.value) {
                "Cannot send null message for non-nullable type with key '$key' in topic '$topic'"
            }
        }
        val stringifiedData = stringifyOutgoing(msg.value)
        log.trace(
            "Sending message {} with key '{}' in topic '{}' with value: {}",
            msg.messageId,
            key,
            topic,
            stringifiedData,
        )
        return connection.send(topic, key, stringifiedData, msg.headers)
    }

    private fun stringifyOutgoing(data: T?): String {
        return adapter.toJson(data)
    }

}

class ConsumerSubclient<T>(
    connection: BrokerConnection,
    client: BrokerClient,
    topic: String,
    key: String,
    options: BrokerClientOptions,
    incomingType: Class<T>,
    private val isNullable: Boolean,
    private val callback: suspend CoroutineScope.(BaseBrokerMessage<T>) -> Unit,
) : BaseSubclient(
    connection,
    client,
    topic,
    key,
    options,
) {

    private val log by Log
    private val adapter: JsonAdapter<T?> = createMoshiAdapter(incomingType)

    override fun doDestroy() {
        client.deregisterSubclient(this)
    }

    internal suspend fun onIncomingMessage(
        value: String,
        headers: BrokerMessageHeaders,
    ) = coroutineScope {
        val data = parseIncoming(value)
        // Disable nullability enforcement for RPC exceptions. The caller has to deal with the unsafe typing now.
        if (!isNullable && (headers !is RpcMessageHeaders || !headers.isException)) {
            checkNotNull(data) {
                "Received null message for non-nullable type with key '$key' in topic '$topic'"
            }
        }
        val message = BrokerMessage(topic, key, data, headers)
        log.trace(
            "Received message {} with key '{}' in topic '{}' with value: {}",
            headers.messageId,
            key,
            topic,
            value,
        )
        @Suppress("UNCHECKED_CAST") // Safe due to above null validation
        val brokerMessage = message as BaseBrokerMessage<T>
        try {
            callback(brokerMessage)
        } catch (ex: Exception) {
            log.error(
                "Uncaught consumer callback error while processing message ${headers.messageId} " +
                        "with key '$key' in topic '$topic'",
                ex,
            )
        }
    }

    private fun parseIncoming(json: String): T? {
        return adapter.fromJson(json)
    }

}
