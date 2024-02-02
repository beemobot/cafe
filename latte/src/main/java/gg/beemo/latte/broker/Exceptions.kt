package gg.beemo.latte.broker

import gg.beemo.latte.broker.rpc.RpcStatus

sealed class BrokerException(message: String?) : Exception(message)
class RpcRequestTimeout(message: String) : BrokerException(message)
class IgnoreRpcRequest : BrokerException("Ignoring RPC request")
class RpcException(val status: RpcStatus) : BrokerException("RPC failed with status $status")
