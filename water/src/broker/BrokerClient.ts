import type { BaseIssue, BaseSchema, InferOutput } from "valibot";
import type {
	BaseSubclient,
	BrokerConnection,
	BrokerMessage,
	BrokerMessageHeaders,
	RpcRequestMessage,
	RpcResponse,
	TopicListener,
} from "@";
import { BrokerClientOptions, ConsumerSubclient, Logger, ProducerSubclient, RpcClient } from "@";

const TAG = "BrokerClient";

export abstract class BrokerClient {
	private readonly topics: Map<string, TopicMetadata> = new Map();

	public constructor(public readonly connection: BrokerConnection) {}

	public consumer<TSchema extends BaseSchema<unknown, unknown, BaseIssue<unknown>>>(
		topic: string,
		key: string,
		schema: TSchema,
		callback: (msg: BrokerMessage<InferOutput<TSchema>>) => Promise<void>,
		options: BrokerClientOptions = new BrokerClientOptions(),
	): ConsumerSubclient<TSchema> {
		Logger.debug(TAG, `Creating consumer for key '${key}' in topic '${topic}'`);
		const client = new ConsumerSubclient(this.connection, this, topic, key, options, schema, callback);
		this.registerSubclient(client);
		return client;
	}

	public producer<T>(
		topic: string,
		key: string,
		options: BrokerClientOptions = new BrokerClientOptions(),
	): ProducerSubclient<T> {
		Logger.debug(TAG, `Creating producer for key '${key}' in topic '${topic}'`);
		const client = new ProducerSubclient(this.connection, this, topic, key, options);
		this.registerSubclient(client);
		return client;
	}

	public rpc<
		RequestTSchema extends BaseSchema<unknown, unknown, BaseIssue<unknown>>,
		ResponseTSchema extends BaseSchema<unknown, unknown, BaseIssue<unknown>>,
	>(
		topic: string,
		key: string,
		requestSchema: RequestTSchema,
		responseSchema: ResponseTSchema,
		callback: (
			msg: RpcRequestMessage<InferOutput<RequestTSchema>, InferOutput<ResponseTSchema>>,
		) => Promise<RpcResponse<InferOutput<ResponseTSchema>>>,
		options: BrokerClientOptions = new BrokerClientOptions(),
	): RpcClient<RequestTSchema, ResponseTSchema> {
		return new RpcClient(this, topic, key, options, requestSchema, responseSchema, callback);
	}

	private registerSubclient(subclient: BaseSubclient): void {
		const topic = subclient.topic;
		if (!this.topics.has(topic)) {
			this.topics.set(topic, new TopicMetadata(this.connection, topic));
		}
		const metadata = this.topics.get(topic)!;
		metadata.registerSubclient(subclient);
	}

	// Private API
	public deregisterSubclient(subclient: BaseSubclient): void {
		const topic = subclient.topic;
		if (this.topics.has(topic)) {
			const metadata = this.topics.get(topic)!;
			metadata.deregisterSubclient(subclient);
			if (metadata.isEmpty) {
				this.topics.delete(topic);
			}
		}
	}

	public destroy(): void {
		Logger.debug(TAG, `Destroying BrokerClient with active topics: ${[...this.topics.keys()]}`);
		while (this.topics.size > 0) {
			const [topic, metadata] = this.topics.entries().next().value as [string, TopicMetadata];
			metadata.destroy();
			this.topics.delete(topic);
		}
	}
}

class TopicMetadata {
	private static readonly TAG = "TopicMetadata";

	private readonly keys: Map<string, KeyMetadata> = new Map();
	private isDestroyed: boolean = false;
	private connectionListener: TopicListener | null = null;

	public constructor(
		private readonly connection: BrokerConnection,
		private readonly topic: string,
	) {}

	public get isEmpty(): boolean {
		return this.keys.size === 0;
	}

	public registerSubclient(subclient: BaseSubclient): void {
		if (subclient.topic !== this.topic) {
			throw new Error(
				`Attempting to register subclient with topic '${subclient.topic}' in TopicMetadata of '${this.topic}'`,
			);
		}
		Logger.debug(
			TopicMetadata.TAG,
			`Adding ${this.constructor.name} for key '${subclient.key}' in topic '${this.topic}'`,
		);
		const metadata = this.getOrCreateKeyMetadata(subclient.key);
		if (subclient instanceof ConsumerSubclient) {
			if (metadata.consumers.size === 0 && this.connectionListener === null) {
				Logger.debug(TopicMetadata.TAG, `Creating new connection listener for topic '${this.topic}'`);
				this.connectionListener = (topic, key, value, headers) => this.onTopicMessage(topic, key, value, headers);
				this.connection.on(this.topic, this.connectionListener);
			}
			metadata.consumers.add(subclient);
		} else if (subclient instanceof ProducerSubclient) {
			metadata.producers.add(subclient);
		}
	}

	public deregisterSubclient(subclient: BaseSubclient): void {
		Logger.debug(
			TopicMetadata.TAG,
			`Removing ${this.constructor.name} for key '${subclient.key}' in topic '${this.topic}'`,
		);
		const metadata = this.getExistingKeyMetadata(subclient.key);
		if (metadata) {
			metadata.producers.delete(subclient as ProducerSubclient<any>);
			metadata.consumers.delete(subclient as ConsumerSubclient<any>);
			this.maybeCleanupKeyMetadata(metadata);
		}
	}

	private maybeCleanupKeyMetadata(metadata: KeyMetadata): void {
		if (metadata.isEmpty) {
			this.keys.delete(metadata.key);
		}
		if (this.isEmpty && this.connectionListener !== null) {
			Logger.debug(TopicMetadata.TAG, `Removing connection listener for topic '${this.topic}' after key cleanup`);
			this.connection.off(this.topic, this.connectionListener);
			this.connectionListener = null;
		}
	}

	private getOrCreateKeyMetadata(key: string): KeyMetadata {
		if (!this.keys.has(key)) {
			this.keys.set(key, new KeyMetadata(key));
		}
		return this.keys.get(key)!;
	}

	private getExistingKeyMetadata(key: string): KeyMetadata | null {
		return this.keys.get(key) ?? null;
	}

	private onTopicMessage(topic: string, key: string, value: string, headers: BrokerMessageHeaders): void {
		const metadata = this.getExistingKeyMetadata(key);
		if (!metadata) {
			return;
		}
		for (const consumer of metadata.consumers) {
			consumer
				.onIncomingMessage(value, headers)
				.catch(e =>
					Logger.error(
						TopicMetadata.TAG,
						`Uncaught error in BrokerClient listener for key '${key}' in topic '${topic}'`,
						e,
					),
				);
		}
	}

	public destroy(): void {
		if (this.isDestroyed) {
			return;
		}
		while (this.keys.size > 0) {
			const [key, metadata] = this.keys.entries().next().value as [string, KeyMetadata];
			metadata.destroy();
			this.keys.delete(key);
		}
		if (this.connectionListener !== null) {
			Logger.debug(TopicMetadata.TAG, `Removing connection listener for topic '${this.topic}' during destroy`);
			this.connection.off(this.topic, this.connectionListener);
			this.connectionListener = null;
		}
	}
}

class KeyMetadata {
	public readonly producers: Set<ProducerSubclient<any>> = new Set();
	public readonly consumers: Set<ConsumerSubclient<any>> = new Set();

	public constructor(public readonly key: string) {}

	public get isEmpty(): boolean {
		return this.producers.size === 0 && this.consumers.size === 0;
	}

	public destroy(): void {
		this.producers.forEach(producer => producer.destroy());
		this.consumers.forEach(consumer => consumer.destroy());
		this.producers.clear();
		this.consumers.clear();
	}
}
