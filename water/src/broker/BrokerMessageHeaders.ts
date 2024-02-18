import { randomUUID } from "node:crypto";
import { BrokerConnection } from "./BrokerConnection.js";
import type { MessageId } from "./BrokerConnection.js";

export const BROKER_HEADER_NAMES = {
    SOURCE_SERVICE: "source-service",
    SOURCE_INSTANCE: "source-instance",
    TARGET_SERVICES: "target-services",
    TARGET_INSTANCES: "target-instances",
    MESSAGE_ID: "message-id",
};

export class BrokerMessageHeaders {

    public readonly headers: Record<string, string>;
    public readonly sourceService: string;
    public readonly sourceInstance: string;
    public readonly targetServices: Set<string>;
    public readonly targetInstances: Set<string>;
    public readonly messageId: MessageId;

    public constructor(
        sourceService: string,
        sourceInstance: string,
        targetServices: Set<string>,
        targetInstances: Set<string>,
    );
    public constructor(
        connection: BrokerConnection,
        targetServices: Set<string>,
        targetInstances: Set<string>,
    );
    public constructor(headers: Record<string, string>);
    public constructor(
        sourceServiceOrHeaders: string | BrokerConnection | Record<string, string>,
        sourceInstanceOrTargetServices?: string | Set<string>,
        targetServicesOrTargetInstances?: Set<string>,
        targetInstances?: Set<string>,
    ) {
        // I hate that JS doesn't have native method overloading
        if (typeof sourceServiceOrHeaders === "string") {
            this.sourceService = sourceServiceOrHeaders;
            this.sourceInstance = sourceInstanceOrTargetServices as string;
            this.targetServices = targetServicesOrTargetInstances as Set<string>;
            this.targetInstances = targetInstances as Set<string>;
            this.messageId = randomUUID();
            this.headers = BrokerMessageHeaders.createHeadersMap(
                this.sourceService,
                this.sourceInstance,
                this.targetServices,
                this.targetInstances,
                this.messageId,
            );
        } else if (sourceServiceOrHeaders instanceof BrokerConnection) {
            const connection: BrokerConnection = sourceServiceOrHeaders;
            this.sourceService = connection.serviceName;
            this.sourceInstance = connection.instanceId;
            this.targetServices = sourceInstanceOrTargetServices as Set<string>;
            this.targetInstances = targetServicesOrTargetInstances as Set<string>;
            this.messageId = randomUUID();
            this.headers = BrokerMessageHeaders.createHeadersMap(
                this.sourceService,
                this.sourceInstance,
                this.targetServices,
                this.targetInstances,
                this.messageId,
            );
        } else {
            this.headers = sourceServiceOrHeaders;
            this.sourceService = BrokerMessageHeaders.getOrThrow(this.headers, BROKER_HEADER_NAMES.SOURCE_SERVICE);
            this.sourceInstance = BrokerMessageHeaders.getOrThrow(this.headers, BROKER_HEADER_NAMES.SOURCE_INSTANCE);
            this.targetServices = new Set((this.headers[BROKER_HEADER_NAMES.TARGET_SERVICES] ?? "").split(",").filter(s => s));
            this.targetInstances = new Set((this.headers[BROKER_HEADER_NAMES.TARGET_INSTANCES] ?? "").split(",").filter(s => s));
            this.messageId = BrokerMessageHeaders.getOrThrow(this.headers, BROKER_HEADER_NAMES.MESSAGE_ID);
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

    private static getOrThrow(headers: Record<string, string>, key: string): string {
        const value = headers[key];
        if (value === undefined) {
            throw new Error(`Missing broker message header '${key}'`);
        }
        return value;
    }

}
