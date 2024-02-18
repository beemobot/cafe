import { randomUUID } from "node:crypto";
import type { IHeaders } from "kafkajs";
import type { IBrokerMessageHeaders } from "../BrokerMessageHeaders.js";
import { BROKER_HEADERS } from "../BrokerMessageHeaders.js";

export class KafkaMessageHeaders implements IBrokerMessageHeaders {
	public static readonly INVALID_CLUSTER_ID = `${-Math.pow(2, 32)}`;

	public constructor(
		public readonly clientId: string,
		public readonly sourceCluster: string,
		public readonly targetClusters: Set<string> = new Set(), // Empty = No target restriction
		public readonly requestId: string = randomUUID()
	) {}

	public serialize(): IHeaders {
		return {
			[BROKER_HEADERS.CLIENT_ID]: this.clientId,
			[BROKER_HEADERS.TARGET_CLUSTERS]: [...this.targetClusters].join(","),
			[BROKER_HEADERS.REQUEST_ID]: this.requestId,
			[BROKER_HEADERS.SOURCE_CLUSTER]: this.sourceCluster.toString(),
		};
	}

	public static from(headers: IHeaders): KafkaMessageHeaders {
		const transformed = KafkaMessageHeaders.headersBufferToString(headers);
		return new KafkaMessageHeaders(
			transformed[BROKER_HEADERS.CLIENT_ID] ?? "default",
			transformed[BROKER_HEADERS.SOURCE_CLUSTER] || KafkaMessageHeaders.INVALID_CLUSTER_ID,
			new Set((transformed[BROKER_HEADERS.TARGET_CLUSTERS] ?? "").split(",").filter(c => c)),
			transformed[BROKER_HEADERS.REQUEST_ID] ?? ""
		);
	}

	private static headersBufferToString(headers: IHeaders): Record<string, string> {
		return Object.fromEntries(
			Object.entries(headers)
				.filter(([_, v]) => v)
				.map(([k, v]) => [k, v!.toString()])
		);
	}
}
