package gg.beemo.latte.broker

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import gg.beemo.latte.util.SuspendingCountDownLatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.single
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

sealed class BaseSubclient<RequestT : Any, ResponseT : Any>(
    protected val connection: BrokerConnection,
    protected val client: BrokerClient,
    val topic: String,
    val key: String,
) {

    internal abstract fun destroy()

    companion object {
        // TODO Investigate if it's a good idea to add a global custom type adapter
        //  to serialize large Longs as Strings for easier JS compatibility.
        //  https://github.com/square/moshi#custom-type-adapters
        @JvmStatic
        protected val moshi: Moshi = Moshi.Builder().build()
    }

}

class ProducerSubclient<T : Any>(
    connection: BrokerConnection,
    client: BrokerClient,
    topic: String,
    key: String,
    requestType: Class<T>,
) : BaseSubclient<T, Unit>(
    connection,
    client,
    topic,
    key,
) {

    private val adapter: JsonAdapter<T?> = moshi.adapter(requestType).nullSafe()

    override fun destroy() {
        client.deregisterProducer(this)
    }

    suspend fun send(
        data: T,
        services: Set<String> = emptySet(),
        instances: Set<String> = emptySet(),
    ): MessageId {
        val strigifiedData = stringifyOutgoing(data)
        val headers = connection.createHeaders(services, instances)
        return connection.send(topic, key, strigifiedData, headers)
    }

    private fun stringifyOutgoing(data: T?): String {
        return adapter.toJson(data)
    }

}

class ConsumerSubclient<T : Any>(
    connection: BrokerConnection,
    client: BrokerClient,
    topic: String,
    key: String,
    incomingType: Class<T>,
    private val callback: suspend CoroutineScope.(T) -> Unit,
) : BaseSubclient<Unit, T>(
    connection,
    client,
    topic,
    key,
) {

    private val adapter: JsonAdapter<T?> = moshi.adapter(incomingType).nullSafe()

    override fun destroy() {
        client.deregisterConsumer(this)
    }

    internal suspend fun onIncomingMessage(
        value: String,
        headers: BaseBrokerMessageHeaders,
    ) = coroutineScope {
        val data = parseIncoming(value)
        val message = BrokerMessage(client, topic, key, data, headers)
        // TODO Better nullability support - let the caller decide if nulls are allowed
        // TODO Return the full BrokerMessage
        callback(message.value!!)
    }

    private fun parseIncoming(json: String): T? {
        return adapter.fromJson(json)
    }

}

class RpcClient<RequestT : Any, ResponseT : Any>(
    private val client: BrokerClient,
    private val topic: String,
    private val key: String,
    requestType: Class<RequestT>,
    private val responseType: Class<ResponseT>,
    private val callback: suspend CoroutineScope.(RequestT) -> ResponseT,
) {

    private val requestProducer = client.producer(topic, key, requestType)

    private val requestConsumer = client.consumer(topic, key, requestType) {
        val result = callback(it)
        val responseProducer = client.producer(
            client.createResponseTopic(topic),
            client.createResponseKey(key),
            responseType,
        )
        responseProducer.send(result) // TODO Send only to source service/instance; need BrokerMessage access for this
        responseProducer.destroy()
    }

    suspend fun call(
        request: RequestT,
        services: Set<String> = emptySet(),
        instances: Set<String> = emptySet(),
        timeout: Duration = 10.seconds,
    ): ResponseT {
        return stream(request, services, instances, timeout, 1).single()
    }

    suspend fun stream(
        request: RequestT,
        services: Set<String> = emptySet(),
        instances: Set<String> = emptySet(),
        timeout: Duration = 10.seconds,
        maxResponses: Int? = null,
    ): Flow<ResponseT> {
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
                client.createResponseTopic(topic),
                client.createResponseKey(key),
                responseType,
            ) {
                // TODO great now I don't have access to the headers with the inReplyTo to validate it
                send(it)
                timeoutLatch?.countDown()
                val count = responseCounter.incrementAndGet()
                if (maxResponses != null && count >= maxResponses) {
                    close()
                }
            }

            messageId.set(requestProducer.send(request, services, instances))

            invokeOnClose {
                responseConsumer.destroy()
            }

            if (timeoutLatch != null) {
                timeoutLatch.awaitThrowing(timeout)
            } else {
                delay(timeout)
            }
            close()
        }

    }

    fun destroy() {
        requestProducer.destroy()
        requestConsumer.destroy()
    }

}
