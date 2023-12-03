package gg.beemo.latte.broker

import com.squareup.moshi.JsonClass
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds


@JsonClass(generateAdapter = true)
data class GreetingRequest(val name: String)

@JsonClass(generateAdapter = true)
data class GreetingResponse(val greeting: String)

class TestBrokerClient(connection: BrokerConnection) : BrokerClient(connection) {

    val greetingRpc = rpc<GreetingRequest, GreetingResponse>(
        topic = "rpc.greetings",
        key = "greeting.requests",
    ) {
        delay(2.seconds)
        return@rpc GreetingResponse("Hello, ${it.value.name}")
    }

    val nullRpc = rpc<Unit?, Unit?>(
        topic = "null",
        key = "null",
    ) {
        if (it.value != null) {
            throw IllegalStateException("nullRpc received non-null")
        }
        return@rpc null
    }

}
