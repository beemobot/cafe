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

// TODO Add error handling, some try-finally to close the producer/consumer even with errors
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
        val strigifiedData = stringifyOutgoing(msg.value)
        log.trace(
            "Sending message {} with key '{}' in topic '{}' with value: {}",
            msg.messageId,
            key,
            topic,
            strigifiedData,
        )
        return connection.send(topic, key, strigifiedData, msg.headers)
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

    override fun destroy() {
        client.deregisterConsumer(this)
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
        callback(message as BaseBrokerMessage<T>)
    }

    private fun parseIncoming(json: String): T? {
        return adapter.fromJson(json)
    }

}

typealias RpcResponse<ResponseT> = Pair<RpcStatus, ResponseT>

class RpcClient<RequestT, ResponseT>(
    client: BrokerClient,
    topic: String,
    key: String,
    options: BrokerClientOptions,
    requestType: Class<RequestT>,
    requestIsNullable: Boolean,
    private val responseType: Class<ResponseT>,
    private val responseIsNullable: Boolean,
    private val callback: suspend CoroutineScope.(BaseRpcRequestMessage<RequestT, ResponseT>) -> RpcResponse<ResponseT>,
) : BaseSubclient(
    client.connection,
    client,
    topic,
    key,
    options,
) {

    private val requestProducer = client.producer(topic, key, options, requestType, requestIsNullable)
    private val requestConsumer = client.consumer(topic, key, options, requestType, requestIsNullable) { msg ->
        val responseProducer = client.producer(
            client.toResponseTopic(topic),
            client.toResponseKey(key),
            options,
            responseType,
            responseIsNullable,
        )

        suspend fun sendResponse(response: ResponseT?, status: RpcStatus, isException: Boolean, isUpdate: Boolean) {
            val responseMsg = RpcResponseMessage(
                client.toResponseTopic(topic),
                client.toResponseKey(key),
                response,
                RpcMessageHeaders(
                    connection,
                    // Send only to source service/instance that initiated this call
                    targetServices = setOf(msg.headers.sourceService),
                    targetInstances = setOf(msg.headers.sourceInstance),
                    inReplyTo = msg.headers.messageId,
                    status,
                    isException,
                    isUpdate,
                ),
            )
            responseProducer.internalSend(responseMsg, bypassNullCheck = isException)
        }

        val rpcMessage = msg.toRpcRequestMessage<ResponseT> { status, data ->
            sendResponse(data, status, isException = false, isUpdate = true)
        }
        try {
            val (status, response) = callback(rpcMessage)
            sendResponse(response, status, false, isUpdate = false)
        } catch (_: IgnoreRpcRequest) {
            return@consumer
        } catch (ex: RpcException) {
            sendResponse(null, ex.status, true, isUpdate = false)
            return@consumer
        } finally {
            responseProducer.destroy()
        }
    }

    suspend fun call(
        request: RequestT,
        services: Set<String> = emptySet(),
        instances: Set<String> = emptySet(),
        timeout: Duration = 10.seconds,
    ): RpcResponseMessage<ResponseT> {
        return stream(request, services, instances, timeout, 1).single()
    }

    suspend fun stream(
        request: RequestT,
        services: Set<String> = emptySet(),
        instances: Set<String> = emptySet(),
        timeout: Duration = 10.seconds,
        maxResponses: Int? = null,
    ): Flow<RpcResponseMessage<ResponseT>> {
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
                val msg = it.toRpcResponseMessage()
                if (msg.headers.inReplyTo != messageId.get()) {
                    return@consumer
                }
                if (msg.headers.isException) {
                    close(RpcException(msg.headers.status))
                    return@consumer
                }
                send(msg)
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
