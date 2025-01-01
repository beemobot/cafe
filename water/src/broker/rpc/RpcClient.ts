import type { BaseIssue, BaseSchema, InferOutput } from "valibot";
import { OnlineEmitter } from "../../util/internal/OnlineEmitter.js";
import { BufferedEmitter } from "../../util/internal/BufferedEmitter.js";
import type {
	BrokerClient,
	BrokerClientOptions,
	BrokerMessage,
	ConsumerSubclient,
	ProducerSubclient,
	RpcRequestMessage,
	RpcResponse,
	RpcStatus,
} from "@";
import {
	BaseSubclient,
	CountDownLatch,
	IgnoreRpcRequest,
	Logger,
	RpcException,
	RpcMessageHeaders,
	RpcRequestTimeout,
	RpcResponseMessage,
} from "@";

export class RpcClient<
	RequestTSchema extends BaseSchema<unknown, unknown, BaseIssue<unknown>>,
	ResponseTSchema extends BaseSchema<unknown, unknown, BaseIssue<unknown>>,
> extends BaseSubclient {
	private static readonly TAG = "RpcClient";

	private readonly requestProducer: ProducerSubclient<InferOutput<RequestTSchema>>;
	private readonly requestConsumer: ConsumerSubclient<RequestTSchema>;
	private readonly responseProducer: ProducerSubclient<InferOutput<ResponseTSchema>>;
	private readonly responseConsumer: ConsumerSubclient<ResponseTSchema>;
	private readonly responses: OnlineEmitter<BrokerMessage<InferOutput<ResponseTSchema>>> = new OnlineEmitter();

	public constructor(
		client: BrokerClient,
		topic: string,
		key: string,
		options: BrokerClientOptions,
		requestSchema: RequestTSchema,
		responseSchema: ResponseTSchema,
		private readonly callback: (
			msg: RpcRequestMessage<InferOutput<RequestTSchema>, InferOutput<ResponseTSchema>>,
		) => Promise<RpcResponse<InferOutput<ResponseTSchema>>>,
	) {
		super(client.connection, client, topic, key, options);

		this.requestProducer = this.client.producer(topic, key, options);
		this.requestConsumer = this.client.consumer(
			topic,
			key,
			requestSchema,
			async msg => {
				const sendResponse = async (
					response: InferOutput<ResponseTSchema> | null,
					status: RpcStatus,
					isException: boolean,
					isUpdate: boolean,
				) => {
					const responseMsg = new RpcResponseMessage(
						this.toResponseTopic(topic),
						this.toResponseKey(key),
						response,
						new RpcMessageHeaders(
							this.connection,
							new Set([msg.headers.sourceService]),
							new Set([msg.headers.sourceInstance]),
							msg.headers.messageId,
							status,
							isException,
							isUpdate,
						),
					);
					await this.responseProducer.internalSend(responseMsg);
				};

				const rpcMessage = msg.toRpcRequestMessage<InferOutput<ResponseTSchema>>(async (status, data) => {
					await sendResponse(data, status, false, true);
				});
				try {
					const [status, response] = await this.callback(rpcMessage);
					await sendResponse(response, status, false, false);
				} catch (e) {
					if (e instanceof IgnoreRpcRequest) {
						return;
					} else if (e instanceof RpcException) {
						await sendResponse(null, e.status, true, false);
						return;
					}
					Logger.error(
						RpcClient.TAG,
						"Uncaught RPC callback error while processing message " +
							`${msg.headers.messageId} with key '$key' in topic '$topic'`,
						e,
					);
				}
			},
			options,
		);
		this.responseProducer = this.client.producer(this.toResponseTopic(topic), this.toResponseKey(key), options);
		this.responseConsumer = this.client.consumer(
			this.toResponseTopic(topic),
			this.toResponseKey(key),
			responseSchema,
			async msg => {
				this.responses.emit(msg);
			},
			options,
		);
	}

	public async call(
		request: InferOutput<RequestTSchema>,
		services: Set<string> = new Set(),
		instances: Set<string> = new Set(),
		timeout: number = 10 * 1000,
	): Promise<RpcResponseMessage<InferOutput<ResponseTSchema>>> {
		const generator = this.stream(request, services, instances, timeout, 1);
		const msg = (await generator.next()).value;
		if (!msg) {
			throw new Error("Unexpected end of single-response stream");
		}
		return msg;
	}

	public async *stream(
		request: InferOutput<RequestTSchema>,
		services: Set<string> = new Set(),
		instances: Set<string> = new Set(),
		timeout: number = 10 * 1000,
		maxResponses: number = Infinity,
	): AsyncGenerator<RpcResponseMessage<InferOutput<ResponseTSchema>>, void> {
		if (timeout === Infinity && maxResponses === Infinity) {
			throw new Error("Must specify either a timeout or a max number of responses");
		}
		if (maxResponses !== Infinity && maxResponses <= 0) {
			throw new Error("maxResponses must be at least 1");
		}
		let responseCounter = 0;
		const timeoutLatch = maxResponses ? new CountDownLatch(maxResponses) : null;
		const timeoutPromise = timeoutLatch?.await(timeout) ?? new Promise(() => {});

		// Start to listen for responses before sending the request to avoid missing any,
		// especially with local short-circuit which will run synchronoously to the request.
		const responses = new BufferedEmitter<BrokerMessage<InferOutput<ResponseTSchema>>>();
		const responseListener = (msg: BrokerMessage<InferOutput<ResponseTSchema>>) => {
			responses.emit(msg);
		};

		try {
			this.responses.addListener(responseListener);
			const messageId = await this.requestProducer.send(request, services, instances);

			while (true) {
				const result = await Promise.race([responses.next(), timeoutPromise]);
				if (typeof result === "boolean") {
					if (result) {
						return;
					} else {
						throw new RpcRequestTimeout(`RPC request timed out after ${timeout} ms`);
					}
				}
				const msg = result.toRpcResponseMessage();
				if (msg.headers.inReplyTo !== messageId) {
					return;
				}
				if (msg.headers.isException) {
					throw new RpcException(msg.headers.status);
				}
				yield msg;
				timeoutLatch?.countDown();
				responseCounter++;
				if (responseCounter >= maxResponses) {
					return;
				}
			}
		} finally {
			this.responses.removeListener(responseListener);
		}
	}

	protected override doDestroy(): void {
		this.requestProducer.destroy();
		this.requestConsumer.destroy();
		this.responseProducer.destroy();
		this.responseConsumer.destroy();
	}

	private toResponseTopic(topic: string): string {
		return this.connection.supportsTopicHotSwap ? `${topic}.responses` : topic;
	}

	private toResponseKey(key: string): string {
		return `${key}.response`;
	}
}
