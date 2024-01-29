package gg.beemo.latte.broker.rpc

class RpcStatus(val code: Int) {

    companion object {
        val OK = RpcStatus(0)
        val UNKNOWN = RpcStatus(999_999)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RpcStatus) return false
        return code == other.code
    }

    override fun hashCode(): Int {
        return code
    }

}
