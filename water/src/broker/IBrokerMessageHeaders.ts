export const BROKER_HEADERS = {
    CLIENT_ID: "client-id",
    REQUEST_ID: "request-id",
    SOURCE_CLUSTER: "source-cluster",
    TARGET_CLUSTERS: "target-clusters",
};

export interface IBrokerMessageHeaders {

    get clientId(): string;
    get sourceCluster(): string;
    get targetClusters(): Set<string>;
    get requestId(): string;

}
