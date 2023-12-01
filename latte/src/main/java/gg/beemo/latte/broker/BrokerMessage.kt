package gg.beemo.latte.broker

data class BrokerMessage<T : Any>(
    val client: BrokerClient,
    val topic: String,
    val key: String,
    val value: T?,
    val headers: BaseBrokerMessageHeaders
) {

    suspend fun respond(data: T?) {
        client.respond(this, data)
    }

}
