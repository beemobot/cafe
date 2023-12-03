package gg.beemo.latte.broker.kafka

import gg.beemo.latte.broker.BaseBrokerMessageHeaders
import gg.beemo.latte.broker.MessageId
import org.apache.kafka.common.header.Headers

class KafkaMessageHeaders(
    sourceService: String,
    sourceInstance: String,
    targetServices: Set<String>,
    targetInstances: Set<String>,
    inReplyTo: MessageId?,
    messageId: MessageId?,
) : BaseBrokerMessageHeaders(
    sourceService,
    sourceInstance,
    targetServices,
    targetInstances,
    inReplyTo,
    messageId,
) {

    constructor(headers: Headers) : this(
        headers.getOrThrow(HEADER_SOURCE_SERVICE),
        headers.getOrThrow(HEADER_SOURCE_INSTANCE),
        splitToSet(headers.getOrDefault(HEADER_TARGET_SERVICES, "")),
        splitToSet(headers.getOrDefault(HEADER_TARGET_INSTANCES, "")),
        headers.getOrNull(HEADER_IN_REPLY_TO),
        headers.getOrThrow(HEADER_MESSAGE_ID),
    )

    fun applyTo(headers: Headers) {
        headers.add(HEADER_SOURCE_SERVICE, sourceService.toByteArray())
        headers.add(HEADER_SOURCE_INSTANCE, sourceInstance.toByteArray())
        headers.add(HEADER_TARGET_SERVICES, joinToString(targetServices).toByteArray())
        headers.add(HEADER_TARGET_INSTANCES, joinToString(targetInstances).toByteArray())
        inReplyTo?.let { headers.add(HEADER_IN_REPLY_TO, it.toByteArray()) }
        headers.add(HEADER_MESSAGE_ID, messageId.toByteArray())
    }

}

private fun Headers.getOrNull(key: String): String? {
    return lastHeader(key)?.let { String(it.value()) }
}

private fun Headers.getOrThrow(key: String): String {
    return getOrNull(key) ?: throw IllegalArgumentException("Missing header '$key'")
}

private fun Headers.getOrDefault(key: String, defaultValue: String): String {
    return getOrNull(key) ?: defaultValue
}
