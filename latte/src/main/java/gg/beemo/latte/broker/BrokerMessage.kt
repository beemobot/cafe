package gg.beemo.latte.broker

data class BrokerMessage<T : Any>(
    val client: BrokerClient<T>,
    val key: String,
    val value: T?,
    val headers: IBrokerMessageHeaders
) {

    suspend fun respond(data: T?, blocking: Boolean = true) {
        client.respond(this, data, blocking)
    }

}
