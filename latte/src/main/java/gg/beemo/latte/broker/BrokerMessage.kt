package gg.beemo.latte.broker

import gg.beemo.latte.broker.rpc.RpcMessageHeaders
import gg.beemo.latte.broker.rpc.RpcRequestMessage
import gg.beemo.latte.broker.rpc.RpcResponseMessage
import gg.beemo.latte.broker.rpc.RpcStatus

open class BrokerMessage<T, H : BrokerMessageHeaders>(
    val topic: String,
    val key: String,
    val value: T,
    val headers: H
) {

    val messageId: String
        get() = headers.messageId

    internal fun <ResponseT> toRpcRequestMessage(
        updateSender: suspend (RpcStatus, ResponseT) -> Unit,
    ): RpcRequestMessage<T, ResponseT, H> {
        return RpcRequestMessage(topic, key, value, headers, updateSender)
    }

    internal fun toRpcResponseMessage(): RpcResponseMessage<T> {
        return RpcResponseMessage(topic, key, value, RpcMessageHeaders(headers))
    }

}

typealias AbstractBrokerMessage<T> = BrokerMessage<T, out BrokerMessageHeaders>
typealias BaseBrokerMessage<T> = BrokerMessage<T, BrokerMessageHeaders>
