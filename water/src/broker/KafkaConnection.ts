import type { Consumer, KafkaMessage, Producer } from "kafkajs";
import { Kafka, logLevel } from "kafkajs";
import { Logger } from "../logging/Logger.js";
import type { IBrokerConnection, BrokerTopicListener } from "./IBrokerConnection.js";
import type { IBrokerMessageHeaders } from "./IBrokerMessageHeaders.js";
import { KafkaMessageHeaders } from "./KafkaMessageHeaders.js";


const TAG = "KafkaConnection";

export class KafkaConnection implements IBrokerConnection {

    private readonly kafka: Kafka;
    private readonly producer: Producer;
    private readonly consumer: Consumer;

    private readonly subscribedTopics: Set<string> = new Set();

    private readonly topicListeners: Map<string, Set<BrokerTopicListener>> = new Map();
    private isRunning: boolean = false;

    public constructor(
        kafkaHost: string,
        public readonly clientId: string,
        consumerGroupId: string,
        public readonly clusterId: string,
    ) {
        this.kafka = new Kafka({
            brokers: [kafkaHost],
            clientId: consumerGroupId,
            logCreator: () => ({ namespace, level, log }) => {
                // eslint-disable-next-line @typescript-eslint/no-unused-vars
                const { message, timestamp, ...other } = log;
                switch (level) {
                    case logLevel.DEBUG:
                        Logger.debug(TAG, `[${namespace}] ${message}`, other);
                        break;
                    case logLevel.INFO:
                        Logger.info(TAG, `[${namespace}] ${message}`, other);
                        break;
                    case logLevel.WARN:
                        Logger.warn(TAG, `[${namespace}] ${message}`, other);
                        break;
                    case logLevel.ERROR:
                    case logLevel.NOTHING:
                        Logger.error(TAG, `[${namespace}] ${message}`, other);
                        break;
                }
            },
        });

        this.producer = this.kafka.producer({
            retry: {
                retries: 2,
            },
            idempotent: true,
        });
        this.consumer = this.kafka.consumer({
            groupId: consumerGroupId,
        });
    }

    public async send(
        topic: string,
        key: string,
        value: string,
        headers: KafkaMessageHeaders,
    ): Promise<string> {
        await this.producer.send({
            topic,
            messages: [{
                key,
                value,
                headers: headers.serialize(),
            }],
        });
        // If the record is meant for ourselves (amongst other clusters),
        // immediately dispatch it to the listeners.
        if (!headers.targetClusters.size || headers.targetClusters.has(this.clusterId)) {
            await this.invokeCallbacks(topic, key, value, headers);
        }
        return headers.requestId;
    }

    public createHeaders(targetClusters?: Set<string>, requestId?: string): IBrokerMessageHeaders {
        return new KafkaMessageHeaders(this.clientId, this.clusterId, targetClusters, requestId);
    }

    public on(topic: string, cb: BrokerTopicListener): void {
        let listeners = this.topicListeners.get(topic);
        if (!listeners) {
            if (this.isRunning) {
                throw new Error("Cannot subscribe to new topic after KafkaConnection has started");
            }
            listeners = new Set();
            this.subscribedTopics.add(topic);
            this.topicListeners.set(topic, listeners);
        }
        listeners.add(cb);
    }

    public off(topic: string, cb: BrokerTopicListener): void {
        const listeners = this.topicListeners.get(topic);
        if (listeners) {
            listeners.delete(cb);
            if (!listeners.size) {
                this.subscribedTopics.delete(topic);
                this.topicListeners.delete(topic);
            }
        }
    }

    public async start(): Promise<void> {
        if (this.isRunning) {
            throw new Error("KafkaConnection is already running!");
        }
        Logger.debug(TAG, "Starting Kafka Connection");
        await this.createTopics();
        await this.createProducer();
        await this.createConsumer();
        this.isRunning = true;
    }

    public async destroy(): Promise<void> {
        await this.consumer.disconnect();
        await this.producer.disconnect();
        this.subscribedTopics.clear();
        this.topicListeners.clear();
        this.isRunning = false;
    }

    private async createTopics(): Promise<void> {
        const targetTopics = [...this.subscribedTopics];
        Logger.debug(TAG, `Creating missing topics, target topics: ${targetTopics}`);
        const admin = this.kafka.admin();
        const existingTopics = await admin.listTopics();
        const missingTopics = targetTopics.filter(t => !existingTopics.includes(t));
        Logger.debug(TAG, `Missing topics: ${missingTopics}`);
        if (missingTopics.length) {
            Logger.info(TAG, "Creating missing topics");
            await admin.createTopics({
                topics: missingTopics.map(t => ({
                    topic: t,
                    numPartitions: 1,
                    replicationFactor: 1,
                })),
            });
        }
        Logger.debug(TAG, "Created all missing topics");
    }

    private async createProducer(): Promise<void> {
        Logger.debug(TAG, "Creating Producer");
        await this.producer.connect();
    }

    private async createConsumer(): Promise<void> {
        Logger.debug(TAG, "Creating Consumer");
        await this.consumer.connect();
        await this.consumer.subscribe({
            topics: [...this.subscribedTopics],
        });
        // TODO Runtime error handling (such as for missing topics)
        await this.consumer.run({
            eachMessage: async ({ topic, message: record }) => {
                void this.handleIncomingRecord(topic, record);
            },
        });
    }

    private async handleIncomingRecord(topic: string, record: KafkaMessage): Promise<void> {
        try {
            const key = record.key?.toString();
            const value = record.value?.toString();
            if (!key || !value || !record.headers) {
                return;
            }

            const headers = KafkaMessageHeaders.from(record.headers);
            if (headers.targetClusters.size && !headers.targetClusters.has(this.clusterId)) {
                // If there is a target cluster restriction and this record wasn't meant for us,
                // discard it immediately without notifying any listeners.
                return;
            }
            if (headers.sourceCluster === this.clusterId) {
                // If this record was sent by ourselves, discard it too, as we already dispatch events
                // to our listeners in `send()` to avoid the round trip through Kafka.
                return;
            }

            await this.invokeCallbacks(topic, key, value, headers);
        } catch (e) {
            Logger.error(TAG, "Uncaught error in internal KafkaConnection record handler", e);
        }
    }

    private async invokeCallbacks(topic: string, key: string, value: string, headers: IBrokerMessageHeaders): Promise<void> {
        const listeners = this.topicListeners.get(topic) ?? [];
        for (const listener of listeners) {
            listener(key, value, headers)?.catch(e => Logger.error(TAG, "Uncaught error in KafkaConnection listener", e));
        }
    }

}
