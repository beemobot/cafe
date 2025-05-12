import type { ClientHttp2Stream, IncomingHttpHeaders, IncomingHttpStatusHeader, ServerHttp2Stream } from "node:http2";
import { logger } from "./logger.js";

export function respondWithError(stream: ServerHttp2Stream, httpStatus: number, message: string): void {
	stream.respond({ ":status": httpStatus }, { waitForTrailers: true });
	stream.on("wantTrailers", () => {
		stream.sendTrailers({
			// https://grpc.io/docs/guides/status-codes/
			// 2 = UNKNOWN
			"grpc-status": "2",
			"grpc-message": message,
		});
	});
}

export function pipeRequest(from: ServerHttp2Stream, to: ClientHttp2Stream): void {
	to.on("timeout", () => {
		logger.error(`Timeout on remote stream ${to.id}`);
	});
	to.on("error", (err: Error) => {
		logger.error("Request Error", err);
	});
	to.on("response", (responseHeaders: IncomingHttpHeaders & IncomingHttpStatusHeader, responseFlags: number) => {
		logger.debug(`  <- Remote stream ${to.id} responded with flags ${responseFlags} and headers`, { ...responseHeaders });
		if (from.destroyed) {
			logger.debug(
				`Server stream ${from.id} has been destroyed, dropping remote stream ${to.id} response`,
			);
			to.destroy();
			return;
		}

		const willHaveTrailers = !to.endAfterHeaders;
		from.respond(responseHeaders, { waitForTrailers: willHaveTrailers });
		if (willHaveTrailers) {
			void setupTrailerForwarding(to, from);
		}

		logger.debug(`  <- Piping to server stream ${from.id}`);
		to.pipe(from);
	});
	to.on("end", () => {
		logger.debug(`  <- Remote stream ${to.id} ended`);
	});

	from.on("end", () => {
		logger.debug(`<- Server stream ${from.id} ended`);
	});
	from.on("close", () => {
		logger.debug(`-> Server stream ${from.id} closed`);
	});

	logger.debug(`  -> Piping to remote stream ${to.id}`);
	// Note: GRPC requests never have trailers, so no trailer forwarding is needed here.
	from.pipe(to);
}


export async function setupTrailerForwarding(from: ClientHttp2Stream, to: ServerHttp2Stream): Promise<void> {
	// Ensure trailers are received AND target stream is ready to receive them.
	const [trailers, toAwaitingTrailers] = await Promise.all([
		new Promise<IncomingHttpHeaders>((resolve, reject) => {
			let resolved = false;
			from.on("trailers", (trailers: IncomingHttpHeaders, flags: number) => {
				logger.debug(`  <- Remote stream ${from.id} sent trailers with flags ${flags}`, { ...trailers });
				resolved = true;
				resolve(trailers);
			});
			from.on("close", () => {
				// If source stream never sent trailers, close target stream as well.
				if (!resolved) {
					logger.debug(`Remote stream ${from.id} closed, dropping server stream ${to.id} trailers`);
					to.end();
					reject(new Error("Source stream closed before sending trailers"));
				}
			});
		}),
		new Promise<typeof to>((resolve, reject) => {
			let resolved = false;
			to.on("wantTrailers", () => {
				logger.debug(`-> Sending trailers to server stream ${to.id} `);
				resolved = true;
				resolve(to);
			});
			to.on("close", () => {
				// If target stream closes before wanting trailers, close source stream as well,
				// since there's nowhere to pipe its response to now.
				if (!resolved) {
					logger.debug(`Server stream ${to.id} closed, dropping remote stream ${from.id} trailers`);
					from.end();
					reject(new Error("Target stream closed before wanting trailers"));
				}
			});
		}),
	]);

	toAwaitingTrailers.sendTrailers(trailers);
}
