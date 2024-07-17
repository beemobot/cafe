import { randomUUID } from "node:crypto";
import { BrokerConnection } from "../index.js";
import type { MessageId } from "./BrokerConnection.js";

const BROKER_HEADER_NAMES = {
    SOURCE_SERVICE: "source-service",
    SOURCE_INSTANCE: "source-instance",
    TARGET_SERVICES: "target-services",
    TARGET_INSTANCES: "target-instances",
    MESSAGE_ID: "message-id",
};

export class BrokerMessageHeaders {

    public readonly headers: Record<string, string>;

    public get sourceService(): string {
        return getOrThrow(this.headers, BROKER_HEADER_NAMES.SOURCE_SERVICE);
    }
    public get sourceInstance(): string {
        return getOrThrow(this.headers, BROKER_HEADER_NAMES.SOURCE_INSTANCE);
    }
    public get targetServices(): Set<string> {
        return new Set((this.headers[BROKER_HEADER_NAMES.TARGET_SERVICES] ?? "").split(",").filter(s => s));
    }
    public get targetInstances(): Set<string> {
        return new Set((this.headers[BROKER_HEADER_NAMES.TARGET_INSTANCES] ?? "").split(",").filter(s => s));
    }
    public get messageId(): MessageId {
        return getOrThrow(this.headers, BROKER_HEADER_NAMES.MESSAGE_ID);
    }

    public constructor(headers: Record<string, string>);
    public constructor(
        connection: BrokerConnection,
        targetServices: Set<string>,
        targetInstances: Set<string>,
    );
    public constructor(
        headersOrConnection: BrokerConnection | Record<string, string>,
        targetServices?: Set<string>,
        targetInstances?: Set<string>,
    ) {
        // I hate that JS doesn't have native method overloading
        if (headersOrConnection instanceof BrokerConnection) {
            const connection = headersOrConnection;
            this.headers = BrokerMessageHeaders.createHeadersMap(
                connection.serviceName,
                connection.instanceId,
                targetServices!,
                targetInstances!,
                null,
            );
        } else {
            this.headers = headersOrConnection;
        }
    }

    protected static createHeadersMap(
        sourceService: string,
        sourceInstance: string,
        targetServices: Set<string>,
        targetInstances: Set<string>,
        messageId: MessageId | null,
        extra: Record<string, string> = {},
    ): Record<string, string> {
        const headers: Record<string, string> = {
            [BROKER_HEADER_NAMES.SOURCE_SERVICE]: sourceService,
            [BROKER_HEADER_NAMES.SOURCE_INSTANCE]: sourceInstance,
            [BROKER_HEADER_NAMES.TARGET_SERVICES]: [...targetServices].join(","),
            [BROKER_HEADER_NAMES.TARGET_INSTANCES]: [...targetInstances].join(","),
            [BROKER_HEADER_NAMES.MESSAGE_ID]: messageId ?? randomUUID(),
            ...extra,
        };
        return headers;
    }

}

export function getOrThrow(headers: Record<string, string>, key: string): string {
    const value = headers[key];
    if (value === undefined) {
        throw new Error(`Missing broker message header '${key}'`);
    }
    return value;
}
