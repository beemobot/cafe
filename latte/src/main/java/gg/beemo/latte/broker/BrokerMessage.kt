package gg.beemo.latte.broker

open class BrokerMessage<T, H : BrokerMessageHeaders>(
    val topic: String,
    val key: String,
    val value: T,
    val headers: H
) {

    val messageId: String
        get() = headers.messageId

    internal fun <ResponseT> toRpcRequestMessage(
        updateSender: suspend (ResponseT, RpcStatus) -> Unit,
    ): RpcRequestMessage<T, ResponseT, H> {
        return RpcRequestMessage(topic, key, value, headers, updateSender)
    }

}

typealias BaseBrokerMessage<T> = BrokerMessage<T, BrokerMessageHeaders>
typealias BaseRpcRequestMessage<RequestT, ResponseT> = RpcRequestMessage<RequestT, ResponseT, BrokerMessageHeaders>
typealias RpcResponseMessage<T> = BrokerMessage<T, RpcMessageHeaders>

class RpcRequestMessage<RequestT, ResponseT, H : BrokerMessageHeaders>(
    topic: String,
    key: String,
    value: RequestT,
    headers: H,
    private val updateSender: suspend (ResponseT, RpcStatus) -> Unit,
) : BrokerMessage<RequestT, H>(topic, key, value, headers) {

    suspend fun sendUpdate(response: ResponseT, status: RpcStatus) {
        updateSender(response, status)
    }

}
