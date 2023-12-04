package gg.beemo.latte.broker

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import gg.beemo.latte.logging.Log
import gg.beemo.latte.util.MoshiInstantAdapter
import gg.beemo.latte.util.MoshiJsLongAdapter
import gg.beemo.latte.util.MoshiUnitAdapter
import gg.beemo.latte.util.SuspendingCountDownLatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.single
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class BrokerClientOptions(
    val useSafeJsLongs: Boolean = false,
)

sealed class BaseSubclient(
    protected val connection: BrokerConnection,
    protected val client: BrokerClient,
    val topic: String,
    val key: String,
    protected val options: BrokerClientOptions,
) {

    internal abstract fun destroy()

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

    override fun destroy() {
        client.deregisterProducer(this)
    }

    suspend fun send(
        data: T,
        services: Set<String> = emptySet(),
        instances: Set<String> = emptySet(),
    ): MessageId {
        return send(data, services, instances, null)
    }

    internal suspend fun send(
        data: T,
        services: Set<String>,
        instances: Set<String>,
        inReplyTo: MessageId?,
    ): MessageId {
        if (!isNullable) {
            requireNotNull(data) {
                "Cannot send null message for non-nullable type with key '$key' in topic '$topic'"
            }
        }
        val strigifiedData = stringifyOutgoing(data)
        val headers = connection.createHeaders(services, instances, inReplyTo)
        log.trace(
            "Sending message {} with key '{}' in topic '{}' with value: {}",
            headers.messageId,
            key,
            topic,
            strigifiedData,
        )
        return connection.send(topic, key, strigifiedData, headers)
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
    private val callback: suspend CoroutineScope.(BrokerMessage<T>) -> Unit,
) : BaseSubclient(
    connection,
    client,
    topic,
    key,
    options,
) {

    private val log by Log
    private val adapter: JsonAdapter<T?> = createMoshiAdapter(incomingType)

    override fun destroy() {
        client.deregisterConsumer(this)
    }

    internal suspend fun onIncomingMessage(
        value: String,
        headers: BaseBrokerMessageHeaders,
    ) = coroutineScope {
        val data = parseIncoming(value)
        if (!isNullable) {
            checkNotNull(data) {
                "Received null message for non-nullable type with key '$key' in topic '$topic'"
            }
        }
        val message = BrokerMessage(client, topic, key, data, headers)
        log.trace(
            "Received message {} with key '{}' in topic '{}' with value: {}",
            headers.messageId,
            key,
            topic,
            value,
        )
        @Suppress("UNCHECKED_CAST") // Safe due to above null validation
        callback(message as BrokerMessage<T>)
    }

    private fun parseIncoming(json: String): T? {
        return adapter.fromJson(json)
    }

}

class RpcClient<RequestT, ResponseT>(
    client: BrokerClient,
    topic: String,
    key: String,
    options: BrokerClientOptions,
    requestType: Class<RequestT>,
    requestIsNullable: Boolean,
    private val responseType: Class<ResponseT>,
    private val responseIsNullable: Boolean,
    private val callback: suspend CoroutineScope.(BrokerMessage<RequestT>) -> ResponseT,
) : BaseSubclient(
    client.connection,
    client,
    topic,
    key,
    options,
) {

    private val requestProducer = client.producer(topic, key, options, requestType, requestIsNullable)

    private val requestConsumer = client.consumer(topic, key, options, requestType, responseIsNullable) {
        val result = try {
            callback(it)
        } catch (_: IgnoreRpcRequest) {
            return@consumer
        }
        val responseProducer = client.producer(
            client.toResponseTopic(topic),
            client.toResponseKey(key),
            options,
            responseType,
            responseIsNullable,
        )
        // Send only to source service/instance that initiated this call
        responseProducer.send(
            result,
            services = setOf(it.headers.sourceService),
            instances = setOf(it.headers.sourceInstance),
            inReplyTo = it.messageId,
        )
        responseProducer.destroy()
    }

    suspend fun call(
        request: RequestT,
        services: Set<String> = emptySet(),
        instances: Set<String> = emptySet(),
        timeout: Duration = 10.seconds,
    ): BrokerMessage<ResponseT> {
        return stream(request, services, instances, timeout, 1).single()
    }

    suspend fun stream(
        request: RequestT,
        services: Set<String> = emptySet(),
        instances: Set<String> = emptySet(),
        timeout: Duration = 10.seconds,
        maxResponses: Int? = null,
    ): Flow<BrokerMessage<ResponseT>> {
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
                client.toResponseTopic(topic),
                client.toResponseKey(key),
                options,
                responseType,
                responseIsNullable,
            ) {
                if (it.headers.inReplyTo != messageId.get()) {
                    return@consumer
                }
                send(it)
                timeoutLatch?.countDown()
                val count = responseCounter.incrementAndGet()
                if (maxResponses != null && count >= maxResponses) {
                    close()
                }
            }

            invokeOnClose {
                responseConsumer.destroy()
            }

            messageId.set(requestProducer.send(request, services, instances))

            if (timeoutLatch != null) {
                try {
                    timeoutLatch.awaitThrowing(timeout)
                } catch (_: TimeoutCancellationException) {
                    throw RpcRequestTimeout("RPC request timed out after $timeout")
                }
            } else {
                delay(timeout)
            }
            close()
        }

    }

    override fun destroy() {
        requestProducer.destroy()
        requestConsumer.destroy()
    }

}

sealed class BrokerException(message: String?) : Exception(message)
class RpcRequestTimeout(message: String) : BrokerException(message)
class IgnoreRpcRequest : BrokerException(null)
