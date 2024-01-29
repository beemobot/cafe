package gg.beemo.latte.broker.rpc

import gg.beemo.latte.broker.BrokerMessage
import gg.beemo.latte.broker.BrokerMessageHeaders

typealias RpcResponse<ResponseT> = Pair<RpcStatus, ResponseT>

typealias BaseRpcRequestMessage<RequestT, ResponseT> = RpcRequestMessage<RequestT, ResponseT, BrokerMessageHeaders>

class RpcRequestMessage<RequestT, ResponseT, H : BrokerMessageHeaders>(
    topic: String,
    key: String,
    value: RequestT,
    headers: H,
    private val updateSender: suspend (RpcStatus, ResponseT) -> Unit,
) : BrokerMessage<RequestT, H>(topic, key, value, headers) {

    suspend fun sendUpdate(status: RpcStatus, response: ResponseT) {
        updateSender(status, response)
    }

}

class RpcResponseMessage<ResponseT>(topic: String, key: String, value: ResponseT, headers: RpcMessageHeaders) :
    BrokerMessage<ResponseT, RpcMessageHeaders>(topic, key, value, headers) {

    val status: RpcStatus
        get() = headers.status

}
