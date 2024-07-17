import type { BrokerMessageHeaders, RpcStatus } from "@";
import { RpcMessageHeaders, RpcRequestMessage, RpcResponseMessage } from "@";

export class BrokerMessage<T, H extends BrokerMessageHeaders = BrokerMessageHeaders> {
	public constructor(
		public readonly topic: string,
		public readonly key: string,
		public readonly value: T,
		public readonly headers: H,
	) {}

	public get messageId(): string {
		return this.headers.messageId;
	}

	// Private API
	public toRpcRequestMessage<ResponseT>(
		updateSender: (status: RpcStatus, data: ResponseT) => Promise<void>,
	): RpcRequestMessage<T, ResponseT, H> {
		return new RpcRequestMessage(this.topic, this.key, this.value, this.headers, updateSender);
	}

	// Private API
	public toRpcResponseMessage(): RpcResponseMessage<T> {
		return new RpcResponseMessage(this.topic, this.key, this.value, new RpcMessageHeaders(this.headers));
	}
}
