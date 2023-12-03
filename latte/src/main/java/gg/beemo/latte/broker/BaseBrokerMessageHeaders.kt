package gg.beemo.latte.broker

import java.util.*

open class BaseBrokerMessageHeaders(
    val sourceService: String,
    val sourceInstance: String,
    val targetServices: Set<String>,
    val targetInstances: Set<String>,
    val inReplyTo: MessageId?,
    messageId: MessageId?,
) {

    val messageId: MessageId = messageId ?: UUID.randomUUID().toString()

    companion object {

        const val HEADER_SOURCE_SERVICE = "source-service"
        const val HEADER_SOURCE_INSTANCE = "source-instance"
        const val HEADER_TARGET_SERVICES = "target-services"
        const val HEADER_TARGET_INSTANCES = "target-instances"
        const val HEADER_MESSAGE_ID = "message-id"
        const val HEADER_IN_REPLY_TO = "in-reply-to"

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
