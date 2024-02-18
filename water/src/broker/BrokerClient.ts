import { Logger } from "../logging/Logger.js";
import { CountDownLatch } from "../util/CountDownLatch.js";
import type { IBrokerConnection } from "./BrokerConnection.js";
import type { IBrokerMessageHeaders } from "./BrokerMessageHeaders.js";
import { BrokerMessage } from "./BrokerMessage.js";

const TAG = "BrokerClient";

export type BrokerEventListener<T> = (msg: BrokerMessage<T>) => void | Promise<void>;
export type BrokerMessageListener<T> = (clusterId: string, msg: BrokerMessage<T>) => void | Promise<void>;
export interface ClusterResult<T> {
	responses: Map<string, BrokerMessage<T>>;
	timeout: boolean;
}

export class BrokerClient<T> {
	private keyListeners: Map<string, Set<BrokerEventListener<T>>> = new Map();

	public constructor(
		private readonly connection: IBrokerConnection,
		private readonly topicName: string
	) {
		Logger.debug(TAG, `Initializing BrokerClient with topic '${topicName}'`);
		connection.on(topicName, this.onTopicMessage.bind(this));
	}

	protected async send(
		key: string,
		obj: T | null,
		headers: IBrokerMessageHeaders = this.connection.createHeaders()
	): Promise<string> {
		return await this.connection.send(this.topicName, key, this.stringify(obj), headers);
	}

	protected async sendClusterRequest(
		key: string,
		obj: T | null,
		timeout: number = 0,
		targetClusters: Set<string> = new Set(),
		expectedResponses: number | null = null,
		messageCallback: BrokerMessageListener<T> | null = null
	): Promise<ClusterResult<T>> {
		const responseKey = this.toResponseKey(key);

		const responses: Map<string, BrokerMessage<T>> = new Map();
		const latch = new CountDownLatch(expectedResponses ?? targetClusters.size);
		let requestId = "";

		const cb: BrokerEventListener<T> = (msg: BrokerMessage<T>) => {
			if (msg.headers.requestId !== requestId) {
				return;
			}
			messageCallback?.(msg.headers.sourceCluster, msg)?.catch(e =>
				Logger.error(TAG, "Uncaught error in sendClusterRequest message callback", e)
			);

			responses.set(msg.headers.sourceCluster, msg);
		};

		this.on(responseKey, cb);
		let timeoutReached = false;
		try {
			requestId = await this.send(key, obj, this.connection.createHeaders(targetClusters));

			timeoutReached = !(await latch.await(timeout));
		} finally {
			this.off(responseKey, cb);
		}

		return {
			responses,
			timeout: timeoutReached,
		};
	}

	protected on(key: string, cb: BrokerEventListener<T>): void {
		let listeners = this.keyListeners.get(key);
		if (!listeners) {
			listeners = new Set();
			this.keyListeners.set(key, listeners);
		}
		listeners.add(cb);
	}

	protected off(key: string, cb: BrokerEventListener<T>): void {
		const listeners = this.keyListeners.get(key);
		if (listeners) {
			listeners.delete(cb);
			if (!listeners.size) {
				this.keyListeners.delete(key);
			}
		}
	}

	// For internal use only. Do not call externally!
	public async _respond(msg: BrokerMessage<T>, data: T | null): Promise<void> {
		const newHeaders = this.connection.createHeaders(new Set([msg.headers.sourceCluster]), msg.headers.requestId);
		await this.send(this.toResponseKey(msg.key), data, newHeaders);
	}

	private parse(json: string): T | null {
		return JSON.parse(json) as T | null;
	}

	private stringify(obj: T | null): string {
		return JSON.stringify(obj);
	}

	private async onTopicMessage(key: string, value: string, headers: IBrokerMessageHeaders): Promise<void> {
		const obj = this.parse(value);
		const msg = new BrokerMessage(this, key, obj, headers);
		const listeners = this.keyListeners.get(key);
		if (!listeners || !listeners.size) {
			return;
		}
		for (const listener of listeners) {
			listener(msg)?.catch(e => Logger.error(TAG, "Uncaught error in BrokerClient listener", e));
		}
	}

	private toResponseKey(key: string): string {
		return `${key}-response`;
	}
}
