import type { BrokerClient } from "./BrokerClient.js";
import type { IBrokerMessageHeaders } from "./IBrokerMessageHeaders.js";

export class BrokerMessage<T> {
	public constructor(
		public readonly client: BrokerClient<T>,
		public readonly key: string,
		public readonly value: T | null,
		public readonly headers: IBrokerMessageHeaders
	) {}

	public async respond(data: T | null): Promise<void> {
		await this.client._respond(this, data);
	}
}
