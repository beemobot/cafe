package gg.beemo.latte.broker

class RpcMessageHeaders(headers: Map<String, String>) : BrokerMessageHeaders(headers) {

    val inReplyTo: MessageId by lazy {
        headers.getOrThrow(HEADER_IN_REPLY_TO)
    }
    val status: RpcStatus by lazy {
        RpcStatus(headers.getOrDefault(HEADER_STATUS, "999_999").toInt())
    }
    val isException: Boolean by lazy {
        headers.getOrDefault(HEADER_IS_EXCEPTION, "false").toBoolean()
    }
    val isUpdate: Boolean by lazy {
        headers.getOrDefault(HEADER_IS_UPDATE, "false").toBoolean()
    }

    constructor(base: BrokerMessageHeaders) : this(base.headers)

    constructor(
        sourceService: String,
        sourceInstance: String,
        targetServices: Set<String>,
        targetInstances: Set<String>,
        inReplyTo: MessageId,
        status: RpcStatus,
        isException: Boolean,
        isUpdate: Boolean,
    ) : this(
        createHeadersMap(
            sourceService,
            sourceInstance,
            targetServices,
            targetInstances,
            null,
            extra = mapOf(
                HEADER_IN_REPLY_TO to inReplyTo,
                HEADER_STATUS to status.code.toString(),
                HEADER_IS_EXCEPTION to isException.toString(),
                HEADER_IS_UPDATE to isUpdate.toString(),
            )
        )
    )

    constructor(
        connection: BrokerConnection,
        targetServices: Set<String>,
        targetInstances: Set<String>,
        inReplyTo: MessageId,
        status: RpcStatus,
        isException: Boolean,
        isUpdate: Boolean,
    ) : this(
        connection.serviceName,
        connection.instanceId,
        targetServices,
        targetInstances,
        inReplyTo,
        status,
        isException,
        isUpdate,
    )

    companion object {

        private const val HEADER_IN_REPLY_TO = "rpc-in-reply-to"
        private const val HEADER_STATUS = "rpc-response-status"
        private const val HEADER_IS_EXCEPTION = "rpc-is-exception"
        private const val HEADER_IS_UPDATE = "rpc-is-update"

    }

}
