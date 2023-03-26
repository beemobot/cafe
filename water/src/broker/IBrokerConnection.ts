import type { IBrokerMessageHeaders } from "./IBrokerMessageHeaders.js";


export type BrokerTopicListener = (key: string, value: string, headers: IBrokerMessageHeaders) => void | Promise<void>;

export interface IBrokerConnection {

    get clientId(): string;
    get clusterId(): string;

    start(): Promise<void>;
    destroy(): Promise<void>;

    on(topic: string, cb: BrokerTopicListener): void;
    off(topic: string, cb: BrokerTopicListener): void;

    send(
        topic: string,
        key: string,
        value: string,
        headers: IBrokerMessageHeaders,
    ): Promise<string>;

    createHeaders(
        targetClusters?: Set<string>,
        requestId?: string,
    ): IBrokerMessageHeaders;

}
