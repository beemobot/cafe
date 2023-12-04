package gg.beemo.latte.broker

data class BrokerMessage<T>(
    val client: BrokerClient,
    val topic: String,
    val key: String,
    val value: T,
    val headers: BaseBrokerMessageHeaders
) {

    val messageId: String
        get() = headers.messageId

}
