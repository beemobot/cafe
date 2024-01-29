package gg.beemo.latte.broker

import com.squareup.moshi.JsonClass
import gg.beemo.latte.broker.rpc.RpcStatus
import gg.beemo.latte.logging.Log
import kotlinx.coroutines.CoroutineScope
import org.junit.jupiter.api.Assertions


@JsonClass(generateAdapter = true)
data class GreetingRequest(val name: String)

@JsonClass(generateAdapter = true)
data class GreetingResponse(val greeting: String)

class TestBrokerClient(
    connection: BrokerConnection,
    scope: CoroutineScope,
) : BrokerClient(connection, scope) {

    private val log by Log

    val greetingRpc = rpc<GreetingRequest, GreetingResponse>(
        topic = "rpc.greetings",
        key = "greeting.requests",
    ) {
        log.info("greetingRpc received request: ${it.value}")
        return@rpc RpcStatus.OK to GreetingResponse("Hello, ${it.value.name}")
    }

    val nullRpc = rpc<Unit?, Unit?>(
        topic = "null",
        key = "null",
    ) {
        log.info("nullRpc received request: ${it.value}")
        Assertions.assertNull(it.value)
        return@rpc RpcStatus(1337) to null
    }

    val exceptionRpc = rpc<Unit?, Unit?>(
        topic = "exception",
        key = "exception",
    ) {
        log.info("exceptionRpc received request: ${it.value}")
        throw RpcException(RpcStatus(1337))
    }

    val safeLongProducer = producer<Long>(
        topic = "long",
        key = "long",
        options = BrokerClientOptions(useSafeJsLongs = true),
    )

    init {
        consumer<String>(
            topic = "long",
            key = "long",
            options = BrokerClientOptions(useSafeJsLongs = false),
        ) {
            log.info("safeLongConsumer received: ${it.value}")
            Assertions.assertEquals("1337", it.value)
        }
    }

}
