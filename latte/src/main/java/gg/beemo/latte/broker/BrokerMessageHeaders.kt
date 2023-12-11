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
        sourceService: String,
        sourceInstance: String,
        targetServices: Set<String>,
        targetInstances: Set<String>,
    ) : this(
        createHeadersMap(
            sourceService,
            sourceInstance,
            targetServices,
            targetInstances,
            null,
        )
    )

    constructor(
        connection: BrokerConnection,
        targetServices: Set<String>,
        targetInstances: Set<String>,
    ) : this(
        connection.serviceName,
        connection.instanceId,
        targetServices,
        targetInstances,
    )

    companion object {

        private const val HEADER_SOURCE_SERVICE = "source-service"
        private const val HEADER_SOURCE_INSTANCE = "source-instance"
        private const val HEADER_TARGET_SERVICES = "target-services"
        private const val HEADER_TARGET_INSTANCES = "target-instances"
        private const val HEADER_MESSAGE_ID = "message-id"

        @JvmStatic
        protected fun createHeadersMap(
            sourceService: String,
            sourceInstance: String,
            targetServices: Set<String>,
            targetInstances: Set<String>,
            messageId: MessageId?,
            extra: Map<String, String> = emptyMap(),
        ): Map<String, String> {
            return mapOf(
                HEADER_SOURCE_SERVICE to sourceService,
                HEADER_SOURCE_INSTANCE to sourceInstance,
                HEADER_TARGET_SERVICES to joinToString(targetServices),
                HEADER_TARGET_INSTANCES to joinToString(targetInstances),
                HEADER_MESSAGE_ID to (messageId ?: UUID.randomUUID().toString()),
            )
        }

        @JvmStatic
        protected fun splitToSet(value: String): Set<String> {
            return value.split(",").filter { it.isNotEmpty() }.toSet()
        }

        @JvmStatic
        protected fun joinToString(value: Set<String>): String {
            return value.joinToString(",")
        }

    }

}

internal fun Map<String, String>.getOrThrow(key: String): String {
    return get(key) ?: throw IllegalArgumentException("Missing broker message header '$key'")
}
