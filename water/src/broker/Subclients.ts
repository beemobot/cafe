import { Logger } from "../logging/Logger.js";
import type { BrokerClient } from "./BrokerClient.js";
import type { BrokerConnection, MessageId } from "./BrokerConnection.js";
import { BrokerMessage } from "./BrokerMessage.js";
import { BrokerMessageHeaders } from "./BrokerMessageHeaders.js";

export class BrokerClientOptions {
    // No options exist in the JS universe at the moment
}

export abstract class BaseSubclient {

    private isDestroyed: boolean = false;

    public constructor(
        protected readonly connection: BrokerConnection,
        protected readonly client: BrokerClient,
        public readonly topic: string,
        public readonly key: string,
        protected readonly options: BrokerClientOptions,
    ) { }

    protected abstract doDestroy(): void;

    public destroy(): void {
        if (!this.isDestroyed) {
            this.isDestroyed = true;
            this.doDestroy();
        }
    }
}

export class ProducerSubclient<T> extends BaseSubclient {

    private static readonly TAG = "ProducerSubclient";

    public constructor(
        connection: BrokerConnection,
        client: BrokerClient,
        topic: string,
        key: string,
        options: BrokerClientOptions,
        private readonly schema: T, // TODO eeeeeh
    ) {
        super(connection, client, topic, key, options);
    }

    protected doDestroy(): void {
        this.client.deregisterSubclient(this);
    }

    public async send(
        data: T,
        services: Set<string> = new Set(),
        instances: Set<string> = new Set(),
    ): Promise<MessageId> {
        const msg = new BrokerMessage(
            this.topic,
            this.key,
            data,
            new BrokerMessageHeaders(
                this.connection,
                services,
                instances,
            ),
        );
        return this.internalSend(msg);
    }

    // Private API
    public async internalSend(msg: BrokerMessage<T | null>): Promise<MessageId> {
        const stringifiedData = this.stringifyOutgoing(msg.value);
        Logger.verbose(ProducerSubclient.TAG, `Sending message ${msg.messageId} with key '${msg.key}' in topic '${msg.topic}'`);
        return this.connection.send(msg.topic, msg.key, stringifiedData, msg.headers);
    }

    private stringifyOutgoing(data: T | null): string {
        return JSON.stringify(data);
    }

}

export class ConsumerSubclient<T> extends BaseSubclient {

    private static readonly TAG = "ConsumerSubclient";

    public constructor(
        connection: BrokerConnection,
        client: BrokerClient,
        topic: string,
        key: string,
        options: BrokerClientOptions,
        private readonly schema: T, // TODO eeeeeh
        private readonly callback: (msg: BrokerMessage<T>) => Promise<void>,
    ) {
        super(connection, client, topic, key, options);
    }

    protected doDestroy(): void {
        this.client.deregisterSubclient(this);
    }

    // Private API
    public async onIncomingMessage(
        value: string,
        headers: BrokerMessageHeaders,
    ): Promise<void> {
        const data = this.parseIncoming(value);
        const msg = new BrokerMessage(
            this.topic,
            this.key,
            data,
            headers,
        );
        Logger.verbose(ConsumerSubclient.TAG, `Received message ${msg.messageId} with key '${msg.key}' in topic '${msg.topic}'`);
        try {
            await this.callback(msg);
        } catch (e) {
            Logger.error(ConsumerSubclient.TAG, "Uncaught consumer callback error while processing message " +
                `${headers.messageId} with key '${this.key}' in topic '${this.topic}'`, e);
        }
    }

    private parseIncoming(data: string): T {
        // TODO schema validation
        return JSON.parse(data);
    }

}
