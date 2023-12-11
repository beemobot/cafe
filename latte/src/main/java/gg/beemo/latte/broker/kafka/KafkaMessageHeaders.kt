package gg.beemo.latte.broker.kafka

import gg.beemo.latte.broker.BrokerMessageHeaders
import gg.beemo.latte.broker.MessageId
import org.apache.kafka.common.header.Headers

// TODO Remove these. Instead, the Base headers will have a Map<String, String> of values,
//  which the connections will read from or put all incoming headers into. The important headers
//  will then be extracted by the base headers class.
// TODO Create RpcMessageHeaders which extends the base headers.
class KafkaMessageHeaders(
    sourceService: String,
    sourceInstance: String,
    targetServices: Set<String>,
    targetInstances: Set<String>,
    messageId: MessageId?,
) : BrokerMessageHeaders(
    sourceService,
    sourceInstance,
    targetServices,
    targetInstances,
    messageId,
) {

    constructor(headers: Headers) : this(
        headers.getOrThrow(HEADER_SOURCE_SERVICE),
        headers.getOrThrow(HEADER_SOURCE_INSTANCE),
        splitToSet(headers.getOrDefault(HEADER_TARGET_SERVICES, "")),
        splitToSet(headers.getOrDefault(HEADER_TARGET_INSTANCES, "")),
        headers.getOrThrow(HEADER_MESSAGE_ID),
    )

    fun applyTo(headers: Headers) {
        headers.add(HEADER_SOURCE_SERVICE, sourceService.toByteArray())
        headers.add(HEADER_SOURCE_INSTANCE, sourceInstance.toByteArray())
        headers.add(HEADER_TARGET_SERVICES, joinToString(targetServices).toByteArray())
        headers.add(HEADER_TARGET_INSTANCES, joinToString(targetInstances).toByteArray())
        headers.add(HEADER_MESSAGE_ID, messageId.toByteArray())
    }

}

private fun Headers.getOrNull(key: String): String? {
    return lastHeader(key)?.let { String(it.value()) }
}

private fun Headers.getOrThrow(key: String): String {
    return getOrNull(key) ?: throw IllegalArgumentException("Missing broker message header '$key'")
}

private fun Headers.getOrDefault(key: String, defaultValue: String): String {
    return getOrNull(key) ?: defaultValue
}
