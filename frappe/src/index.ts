import "dotenv/config";
import type { ServerHttp2Stream, IncomingHttpHeaders, IncomingHttpStatusHeader } from "node:http2";
import { createServer } from "node:http2";
import type { Socket } from "node:net";
import { ClusterConnections } from "./ClusterConnections.js";
import { GRPC_GUILD_ID_HEADER } from "./constants.js";

// https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md

const clusterConnections = new ClusterConnections();

const server = createServer();
server.on("error", (err: Error) => {
	console.error("Uncaught Server Error", err);
});
server.on("connection", (socket: Socket) => {
	console.log(`New connection from ${socket.remoteAddress}:${socket.remotePort}`);
});
// eslint-disable-next-line @typescript-eslint/no-misused-promises
server.on("stream", async (stream: ServerHttp2Stream, headers: IncomingHttpHeaders, flags: number) => {
	stream.on("timeout", () => {
		console.error(`Timeout on server stream ${stream.id}`);
	});

	console.log();
	console.log(`<- Server stream ${stream.id} with flags ${flags} and headers`, { ...headers });

	const guildId = headers[GRPC_GUILD_ID_HEADER];
	if (!guildId) {
		// TODO Send error? Have to construct a custom gRPC message lol
		//      Surely there is a lib for that.
		//      Or I just rawdog it, it's just some headers and trailers, right?
		stream.respond({ ":status": 400 }, { waitForTrailers: true });
		stream.on("wantTrailers", () => {
			stream.sendTrailers({ "grpc-status": "2", "grpc-message": "Missing Guild ID" });
		});
		return;
	}
	if (Array.isArray(guildId)) {
		// TODO same error response method as above with different message
		stream.end();
		return;
	}

	const client = await clusterConnections.getConnection(guildId);
	if (!client) {
		// TODO same error response method as above with different message
		stream.end();
		return;
	}

	const req = client.request(headers);

	req.on("timeout", () => {
		console.error(`Timeout on remote stream ${req.id}`);
	});
	req.on("error", (err: Error) => {
		console.error("Request Error", err);
	});
	req.on("response", (headers: IncomingHttpHeaders & IncomingHttpStatusHeader, flags: number) => {
		console.log(`  <- Remote stream ${req.id} responded with flags ${flags} and headers`, { ...headers });
		if (stream.destroyed) {
			console.log(`Server stream ${stream.id} has been destroyed, dropping remote stream ${req.id} response`);
			req.end();
		}
		stream.respond(headers, { waitForTrailers: true });
		req.pipe(stream);
	});
	req.on("trailers", (trailers: IncomingHttpHeaders, flags: number) => {
		console.log(`  <- Remote stream ${req.id} sent trailers with flags ${flags}`, { ...trailers });
		if (stream.destroyed) {
			console.log(`Server stream ${stream.id} has been destroyed, dropping remote stream ${req.id} trailers`);
			req.end();
		}
		stream.on("wantTrailers", () => {
			console.log(`-> Sending trailers to server stream ${stream.id} `);
			stream.sendTrailers(trailers);
		});
	});
	req.on("end", () => {
		console.log(`  <- Remote stream ${req.id} ended`);
	});

	stream.on("end", () => {
		console.log(`<- Server stream ${stream.id} ended`);
	});
	stream.on("close", () => {
		console.log(`-> Server stream ${stream.id} closed`);
		req.close();
	});

	console.log(`  -> Piping to remote stream ${req.id}`);
	stream.pipe(req);
});

const port = +(process.env.GRPC_PROXY_SERVER_PORT ?? "") || 1337;
server.listen(port);
console.log(`Listening on port ${port}`);
