package gg.beemo.latte.broker

sealed class BrokerException(message: String?) : Exception(message)
class RpcRequestTimeout(message: String) : BrokerException(message)
class IgnoreRpcRequest : BrokerException(null)
