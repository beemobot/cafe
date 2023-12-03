package gg.beemo.latte.broker

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class BrokerClientTest {

    private val connection = LocalConnection()
    private val client = TestBrokerClient(connection)

    @Test
    fun `test greeting RPC`() {
        runBlocking {
            val response = client.greetingRpc.call(GreetingRequest("Beemo"))
            Assertions.assertEquals("Hello, Beemo", response.value.greeting)
        }
    }

    @Test
    fun `test null RPC`() {
        runBlocking {
            val response = client.nullRpc.call(null)
            Assertions.assertNull(response.value)
        }
    }

}
