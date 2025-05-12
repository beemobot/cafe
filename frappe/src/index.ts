import "dotenv/config";
import type { ServerHttp2Stream, IncomingHttpHeaders } from "node:http2";
import { createServer } from "node:http2";
import type { Socket } from "node:net";
import { ClusterConnections } from "./ClusterConnections.js";
import { GRPC_GUILD_ID_HEADER } from "./constants.js";
import { pipeRequest, respondWithError } from "./utils.js";
import { logger } from "./logger.js";

// https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md

const clusterConnections = new ClusterConnections();

const server = createServer();
server.on("error", (err: Error) => {
	logger.error("Uncaught Server Error", err);
});
server.on("connection", (socket: Socket) => {
	logger.info(`New connection from ${socket.remoteAddress}:${socket.remotePort}`);
});
server.on(
	"stream",
	// eslint-disable-next-line @typescript-eslint/no-misused-promises
	async (originalRequest: ServerHttp2Stream, requestHeaders: IncomingHttpHeaders, requestFlags: number) => {
		originalRequest.on("timeout", () => {
			logger.error(`Timeout on server stream ${originalRequest.id}`);
		});

		logger.info(`<- Server stream ${originalRequest.id} with flags ${requestFlags} and headers`, { ...requestHeaders });

		const guildId = requestHeaders[GRPC_GUILD_ID_HEADER];
		if (!guildId) {
			respondWithError(originalRequest, 400, "Missing Guild ID");
			return;
		}
		if (Array.isArray(guildId)) {
			respondWithError(originalRequest, 400, "Multiple Guild IDs in headers");
			return;
		}

		const client = await clusterConnections.getConnection(guildId);
		if (!client) {
			respondWithError(originalRequest, 400, "Cannot find Cluster matching the Guild ID");
			return;
		}

		const subRequest = client.request(requestHeaders);
		pipeRequest(originalRequest, subRequest);
	},
);

const port = +(process.env.GRPC_PROXY_SERVER_PORT ?? "") || 1337;
server.listen(port);
logger.info(`Listening on port ${port}`);
