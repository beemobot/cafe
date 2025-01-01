import type { BrokerMessageHeaders, RpcMessageHeaders, RpcStatus } from "@";
import { BrokerMessage } from "@";

export type RpcResponse<ResponseT> = [RpcStatus, ResponseT];

export class RpcRequestMessage<
	RequestT,
	ResponseT,
	H extends BrokerMessageHeaders = BrokerMessageHeaders,
> extends BrokerMessage<RequestT, H> {
	public constructor(
		topic: string,
		key: string,
		value: RequestT,
		headers: H,
		private readonly updateSender: (stats: RpcStatus, data: ResponseT) => Promise<void>,
	) {
		super(topic, key, value, headers);
	}

	public async sendUpdate(status: RpcStatus, data: ResponseT): Promise<void> {
		await this.updateSender(status, data);
	}
}

export class RpcResponseMessage<ResponseT> extends BrokerMessage<ResponseT, RpcMessageHeaders> {
	public constructor(topic: string, key: string, value: ResponseT, headers: RpcMessageHeaders) {
		super(topic, key, value, headers);
	}

	public get status(): RpcStatus {
		return this.headers.status;
	}
}
