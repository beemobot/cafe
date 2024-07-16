import type { Consumer, Publisher } from "rabbitmq-client";
import { Connection } from "rabbitmq-client";
import type { MessageId } from "../BrokerConnection.js";
import { BrokerConnection } from "../BrokerConnection.js";
import { BrokerMessageHeaders } from "../BrokerMessageHeaders.js";
import { Logger } from "../../logging/Logger.js";

export class RabbitConnection extends BrokerConnection {

    private static readonly TAG = "RabbitConnection";

    public override supportsTopicHotSwap: boolean = true;
    public override deferInitialTopicCreation: boolean = true;

    private connection: Connection | null = null;
    private readonly consumers: Map<string, Consumer> = new Map();
    private readonly publishers: Map<string, Publisher> = new Map();

    public constructor(
        private readonly rabbitHosts: string[],
        public override readonly serviceName: string,
        public override readonly instanceId: string,
        private readonly useTls: boolean = false,
        private readonly username: string = "guest",
        private readonly password: string = "guest",
    ) {
        super();
    }

    public override async abstractStart(): Promise<void> {
        this.connection = new Connection({
            hosts: this.rabbitHosts,
            username: this.username,
            password: this.password,
            tls: this.useTls,
            connectionName: `${this.serviceName}: ${this.instanceId}`,
        });
        this.connection.on("connection.blocked", reason => {
            Logger.warn(RabbitConnection.TAG, `RabbitMQ server blocked connection for reason: ${reason}`);
        });
        this.connection.on("connection.unblocked", () => {
            Logger.info(RabbitConnection.TAG, "RabbitMQ server unblocked connection");
        });
        await new Promise<void>((resolve, reject) => {
            // Use `on` instead of `once` to re-use the same logging code for future connection updates
            this.connection!.on("connection", () => {
                Logger.info(RabbitConnection.TAG, "RabbitMQ connection (re)established");
                resolve();
            });
            this.connection!.on("error", error => {
                Logger.error(RabbitConnection.TAG, "Error in RabbitMQ connection", error);
                reject(error);
            });
        });
    }

    public override async destroy(): Promise<void> {
        await super.destroy();
        await this.connection?.close();
    }

    public override async abstractSend(topic: string, key: string, value: string, headers: BrokerMessageHeaders): Promise<MessageId> {
        if (this.shouldDispatchExternallyAfterShortCircuit(topic, key, value, headers)) {
            let publisher = this.publishers.get(topic);
            if (!publisher) {
                publisher = this
                    .ensureConnection()
                    .createPublisher(this.commonPublisherConsumerArguments(topic));
                this.publishers.set(topic, publisher);
            }
            await publisher.send({
                exchange: topic,
                routingKey: key,
                headers: headers.headers,
                messageId: headers.messageId,
                durable: true,
            }, value);
        }
        return headers.messageId;
    }

    protected override createTopic(topic: string): void {
        if (this.consumers.has(topic)) {
            return;
        }
        const queueName = this.createQueueName(topic);
        this.consumers.set(topic, this.ensureConnection().createConsumer({
            queue: queueName,
            ...this.commonPublisherConsumerArguments(topic),
        }, async (msg, reply) => {
            const key = msg.routingKey;
            const value = msg.body instanceof Buffer ? msg.body.toString("utf8") : String(msg.body);
            const headers = new BrokerMessageHeaders(msg.headers ?? {});
            this.dispatchIncomingMessage(topic, key, value, headers);
        }));
    }

    protected override removeTopic(topic: string): void {
        const consumer = this.consumers.get(topic);
        if (consumer) {
            consumer.close().catch(e => {
                Logger.error(RabbitConnection.TAG, `Error closing consumer for topic ${topic}`, e);
            });
            this.consumers.delete(topic);
        }
    }

    private ensureConnection(): Connection {
        const connection = this.connection;
        if (!connection) {
            throw new Error("Connection not open");
        }
        return connection;
    }

    private createQueueName(topic: string): string {
        return `${this.serviceName}.${this.instanceId}.${topic}`;
    }

    private commonPublisherConsumerArguments(topic: string) {
        const exchangeName = topic;
        const queueName = this.createQueueName(topic);
        const routingKey = "#";
        return {
            queues: [{
                queue: queueName,
                durable: true,
                exclusive: false,
                autoDelete: false,
            }],
            exchanges: [{
                exchange: exchangeName,
                type: "topic",
                durable: true,
            }],
            queueBindings: [{
                queue: queueName,
                exchange: exchangeName,
                routingKey,
            }],
        };
    }

}
