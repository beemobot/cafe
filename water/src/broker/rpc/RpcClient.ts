import { Logger } from "../../logging/Logger.js";
import type { BrokerClient } from "../BrokerClient.js";
import { IgnoreRpcRequest, RpcException } from "../Exceptions.js";
import type { BrokerClientOptions, ConsumerSubclient, ProducerSubclient } from "../Subclients.js";
import { BaseSubclient } from "../Subclients.js";
import type { RpcRequestMessage, RpcResponse } from "./RpcMessage.js";
import { RpcResponseMessage } from "./RpcMessage.js";
import { RpcMessageHeaders } from "./RpcMessageHeaders.js";
import type { RpcStatus } from "./RpcStatus.js";

export class RpcClient<RequestT, ResponseT> extends BaseSubclient {

    private static readonly TAG = "RpcClient";

    private readonly requestProducer: ProducerSubclient<RequestT>;
    private readonly requestConsumer: ConsumerSubclient<RequestT>;
    private readonly responseProducer: ProducerSubclient<ResponseT>;
    private readonly responseConsumer: ConsumerSubclient<ResponseT>;

    public constructor(
        client: BrokerClient,
        topic: string,
        key: string,
        options: BrokerClientOptions,
        private readonly requestSchema: RequestT, // TODO eeeeeh
        private readonly responseSchema: ResponseT, // TODO eeeeeh
        private readonly callback: (msg: RpcRequestMessage<RequestT, ResponseT>) => Promise<RpcResponse<ResponseT>>,
    ) {
        super(client.connection, client, topic, key, options);

        this.requestProducer = this.client.producer(
            topic,
            key,
            requestSchema,
            options,
        );
        this.requestConsumer = this.client.consumer(
            topic,
            key,
            requestSchema,
            options,
            async msg => {
                const sendResponse = async (
                    response: ResponseT | null,
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

                const rpcMessage = msg.toRpcRequestMessage<ResponseT>(async (status, data) => {
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
                    Logger.error(RpcClient.TAG, "Uncaught RPC callback error while processing message "
                        + `${msg.headers.messageId} with key '$key' in topic '$topic'`, e);
                }
            },
        );
        this.responseProducer = this.client.producer(
            this.toResponseTopic(topic),
            this.toResponseKey(key),
            responseSchema,
            options,
        );
        this.responseConsumer = this.client.consumer(
            this.toResponseTopic(topic),
            this.toResponseKey(key),
            responseSchema,
            options,
            async msg => {
                // TODO
            },
        );
    }

    public async call(
        request: RequestT,
        services: Set<string> = new Set(),
        instances: Set<string> = new Set(),
        timeout: number = 10 * 1000,
    ): Promise<RpcResponseMessage<ResponseT>> {
        return this.stream(request, services, instances, timeout, 1);
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
