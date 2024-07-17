import { getOrThrow } from "../../util/internal/util.js";
import type { MessageId } from "@";
import { BrokerConnection, BrokerMessageHeaders, RpcStatus } from "@";

const RPC_HEADER_NAMES = {
	IN_REPLY_TO: "rpc-in-reply-to",
	STATUS: "rpc-response-status",
	IS_EXCEPTION: "rpc-is-exception",
	IS_UPDATE: "rpc-is-update",
};

export class RpcMessageHeaders extends BrokerMessageHeaders {
	public get inReplyTo(): MessageId {
		return getOrThrow(this.headers, RPC_HEADER_NAMES.IN_REPLY_TO);
	}
	public get status(): RpcStatus {
		return new RpcStatus(safeToInt(getOrThrow(this.headers, RPC_HEADER_NAMES.STATUS)));
	}
	public get isException(): boolean {
		return this.headers[RPC_HEADER_NAMES.IS_EXCEPTION]?.toLowerCase() === "true";
	}
	public get isUpdate(): boolean {
		return this.headers[RPC_HEADER_NAMES.IS_UPDATE]?.toLowerCase() === "true";
	}

	public constructor(base: Record<string, string> | BrokerMessageHeaders);
	public constructor(
		connection: BrokerConnection,
		targetServices: Set<string>,
		targetInstances: Set<string>,
		inReplyTo: MessageId,
		status: RpcStatus,
		isException: boolean,
		isUpdate: boolean,
	);
	public constructor(
		baseOrConnection: Record<string, string> | BrokerMessageHeaders | BrokerConnection,
		targetServices?: Set<string>,
		targetInstances?: Set<string>,
		inReplyTo?: MessageId,
		status?: RpcStatus,
		isException?: boolean,
		isUpdate?: boolean,
	) {
		if (baseOrConnection instanceof BrokerMessageHeaders) {
			super(baseOrConnection.headers);
		} else if (baseOrConnection instanceof BrokerConnection) {
			const connection = baseOrConnection;
			super(
				BrokerMessageHeaders.createHeadersMap(
					connection.serviceName,
					connection.instanceId,
					targetServices!,
					targetInstances!,
					null,
					{
						[RPC_HEADER_NAMES.IN_REPLY_TO]: inReplyTo!,
						[RPC_HEADER_NAMES.STATUS]: status!.code.toString(),
						[RPC_HEADER_NAMES.IS_EXCEPTION]: isException!.toString(),
						[RPC_HEADER_NAMES.IS_UPDATE]: isUpdate!.toString(),
					},
				),
			);
		} else {
			super(baseOrConnection);
		}
	}
}

function safeToInt(value: string): number {
	const parsed = parseInt(value, 10);
	if (isNaN(parsed)) {
		throw new Error(`Failed to parse integer from value: ${value}`);
	}
	return parsed;
}
