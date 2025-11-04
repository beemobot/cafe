import type { ClientHttp2Session } from "node:http2";
import { connect } from "node:http2";
import { logger } from "./logger.js";

// TODO gRPC lib for cluster lookup:
//       - Preferred:    https://github.com/deeplay-io/nice-grpc/tree/master/packages/nice-grpc
//       - Unmaintained: https://github.com/malijs/mali

export class ClusterConnections {
	// Cluster ID -> Connection
	private connectionCache = new Map<string, ClientHttp2Session>();

	async getConnection(guildId: string): Promise<ClientHttp2Session | null> {
		const existingConnection = this.connectionCache.get(guildId);
		if (existingConnection) {
			return existingConnection;
		}

		// Simulate waiting for subrequest to get correct cluster
		await new Promise(r => setTimeout(r, 1000));
		const clusterId = "TODO";
		const connection = this.createAndCacheClusterConnection(clusterId, "http://localhost:1337");

		return connection;
	}

	private createAndCacheClusterConnection(clusterId: string, endpoint: string): ClientHttp2Session {
		if (this.connectionCache.has(clusterId)) {
			throw new Error(`Connection to cluster ${clusterId} already exists`);
		}

		// TODO Return null if connection failed?
		const connection = connect(endpoint);

		connection.on("error", (err: Error) => {
			logger.error(`Uncaught Client Error for cluster ${clusterId} (${endpoint}), closing connection`, err);
			this.connectionCache.delete(clusterId);
			connection.close();
		});
		connection.on("timeout", () => {
			logger.error(`Connection to cluster ${clusterId} (${endpoint}) timed out, closing connection`);
			this.connectionCache.delete(clusterId);
			connection.close();
		});
		connection.on("close", () => {
			logger.info(`Connection to cluster ${clusterId} (${endpoint}) closed`);
			this.connectionCache.delete(clusterId);
		});

		this.connectionCache.set(clusterId, connection);
		return connection;
	}
}
