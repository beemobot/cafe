package gg.beemo.latte.broker.rpc

import gg.beemo.latte.broker.*
import gg.beemo.latte.logging.Log
import gg.beemo.latte.util.SuspendingCountDownLatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.single
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


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

    private val log by Log

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
        } catch (ex: Exception) {
            log.error(
                "Uncaught RPC callback error while processing message ${msg.headers.messageId} " +
                        "with key '$key' in topic '$topic'",
                ex,
            )
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
                // Close the flow if we receive an exception
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

    override fun doDestroy() {
        requestProducer.destroy()
        requestConsumer.destroy()
    }

}
