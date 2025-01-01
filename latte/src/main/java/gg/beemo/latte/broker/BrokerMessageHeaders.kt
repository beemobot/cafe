package gg.beemo.latte.broker

import java.util.UUID

open class BrokerMessageHeaders(val headers: Map<String, String>) {

    val sourceService: String by lazy {
        headers.getOrThrow(HEADER_SOURCE_SERVICE)
    }
    val sourceInstance: String by lazy {
        headers.getOrThrow(HEADER_SOURCE_INSTANCE)
    }
    val targetServices: Set<String> by lazy {
        splitToSet(headers.getOrDefault(HEADER_TARGET_SERVICES, ""))
    }
    val targetInstances: Set<String> by lazy {
        splitToSet(headers.getOrDefault(HEADER_TARGET_INSTANCES, ""))
    }
    val messageId: MessageId by lazy {
        headers.getOrThrow(HEADER_MESSAGE_ID)
    }

    constructor(
        connection: BrokerConnection,
        targetServices: Set<String>,
        targetInstances: Set<String>,
    ) : this(
        createHeadersMap(
            connection.serviceName,
            connection.instanceId,
            targetServices,
            targetInstances,
            null,
        ),
    )

    companion object {

        private const val HEADER_SOURCE_SERVICE = "source-service"
        private const val HEADER_SOURCE_INSTANCE = "source-instance"
        private const val HEADER_TARGET_SERVICES = "target-services"
        private const val HEADER_TARGET_INSTANCES = "target-instances"
        private const val HEADER_MESSAGE_ID = "message-id"

        // Needs to be JvmStatic to be used in subclasses
        @JvmStatic
        protected fun createHeadersMap(
            sourceService: String,
            sourceInstance: String,
            targetServices: Set<String>,
            targetInstances: Set<String>,
            messageId: MessageId?,
            extra: Map<String, String> = emptyMap(),
        ): Map<String, String> {
            val headers = HashMap<String, String>()
            headers[HEADER_SOURCE_SERVICE] = sourceService
            headers[HEADER_SOURCE_INSTANCE] = sourceInstance
            headers[HEADER_TARGET_SERVICES] = targetServices.joinToString(",")
            headers[HEADER_TARGET_INSTANCES] = targetInstances.joinToString(",")
            headers[HEADER_MESSAGE_ID] = messageId ?: UUID.randomUUID().toString()
            headers.putAll(extra)
            return headers
        }

        protected fun splitToSet(value: String): Set<String> {
            return value.split(",").filter { it.isNotEmpty() }.toSet()
        }

    }

}

internal fun Map<String, String>.getOrThrow(key: String): String {
    return get(key) ?: throw IllegalArgumentException("Missing broker message header '$key'")
}
