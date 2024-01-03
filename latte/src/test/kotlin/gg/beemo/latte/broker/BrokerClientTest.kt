package gg.beemo.latte.broker

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BrokerClientTest {

    private val connection = LocalConnection()

    @Test
    fun `test greeting RPC`() = withTestClient { client ->
        val response = client.greetingRpc.call(GreetingRequest("Beemo"))
        Assertions.assertEquals("Hello, Beemo", response.value.greeting)
        Assertions.assertEquals(RpcStatus.OK, response.headers.status)
    }

    @Test
    fun `test null RPC`() = withTestClient { client ->
        val response = client.nullRpc.call(null)
        Assertions.assertNull(response.value)
        Assertions.assertEquals(RpcStatus(1337), response.headers.status)
    }

    @Test
    fun `test exception RPC`() = withTestClient { client ->
        val exception = assertThrows<RpcException> {
            client.exceptionRpc.call(null)
        }
        Assertions.assertEquals(RpcStatus(1337), exception.status)
    }

    @Test
    fun `test safe Long serializer`() = withTestClient { client ->
        client.safeLongProducer.send(1337)
    }

    private fun withTestClient(block: suspend (TestBrokerClient) -> Unit) = runTest {
        val client = TestBrokerClient(connection, this)
        try {
            block(client)
        } finally {
            client.destroy(false)
        }
    }

}
