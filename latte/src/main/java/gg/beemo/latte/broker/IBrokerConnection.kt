package gg.beemo.latte.broker

fun interface TopicListener {
    suspend fun onMessage(key: String, value: String, headers: IBrokerMessageHeaders)
}

interface IBrokerConnection<Headers : IBrokerMessageHeaders> {

    val clientId: String
    val clusterId: String

    fun start()
    fun destroy()

    fun on(topic: String, cb: TopicListener)
    fun off(topic: String, cb: TopicListener)

    suspend fun send(
        topic: String,
        key: String,
        value: String,
        headers: Headers,
        blocking: Boolean = true,
    ): String

    fun createHeaders(targetClusters: Set<String>? = null, requestId: String? = null): IBrokerMessageHeaders

}
