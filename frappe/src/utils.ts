import type { ClientHttp2Stream, IncomingHttpHeaders, IncomingHttpStatusHeader, ServerHttp2Stream } from "node:http2";
import { logger } from "./logger.js";

export function respondWithError(stream: ServerHttp2Stream, httpStatus: number, message: string): void {
	logger.debug(`Responding to request ${stream.id} with error ${httpStatus}: ${message}`);
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
		logger.error(`Timeout on subrequest ${to.id}`);
	});
	to.on("error", (err: Error) => {
		logger.error("Rubaequest Error", err);
	});
	to.on("response", (responseHeaders: IncomingHttpHeaders & IncomingHttpStatusHeader, responseFlags: number) => {
		logger.debug(`  <- Subrequest ${to.id} responded with flags ${responseFlags} and headers`, { ...responseHeaders });
		if (from.destroyed) {
			logger.debug(`Original request ${from.id} has been destroyed, dropping subrequest ${to.id} response`);
			to.destroy();
			return;
		}

		const willHaveTrailers = !to.endAfterHeaders;
		from.respond(responseHeaders, { waitForTrailers: willHaveTrailers });
		if (willHaveTrailers) {
			void setupTrailerForwarding(to, from);
		}

		logger.debug(`  <- Piping subrequest ${to.id} response to original request ${from.id}`);
		to.pipe(from);
	});
	to.on("end", () => {
		logger.debug(`  <- Subrequest ${to.id} ended`);
	});

	from.on("end", () => {
		logger.debug(`<- Request ${from.id} ended`);
	});
	from.on("close", () => {
		logger.debug(`-> Request ${from.id} closed`);
	});

	logger.debug(`  -> Piping request ${from.id} to subrequest ${to.id}`);
	// Note: GRPC requests never have trailers, so no trailer forwarding is needed here.
	from.pipe(to);
}

export async function setupTrailerForwarding(from: ClientHttp2Stream, to: ServerHttp2Stream): Promise<void> {
	// Ensure trailers are received AND target stream is ready to receive them.
	const [trailers, toAwaitingTrailers] = await Promise.all([
		new Promise<IncomingHttpHeaders>((resolve, reject) => {
			let resolved = false;
			from.on("trailers", (trailers: IncomingHttpHeaders, flags: number) => {
				logger.debug(`  <- Subrequest ${from.id} sent trailers with flags ${flags}`, { ...trailers });
				resolved = true;
				resolve(trailers);
			});
			from.on("close", () => {
				// If source stream never sent trailers, close target stream as well.
				if (!resolved) {
					logger.debug(`Subrequest ${from.id} closed, dropping request ${to.id} trailers`);
					to.end();
					reject(new Error("Source stream closed before sending trailers"));
				}
			});
		}),
		new Promise<typeof to>((resolve, reject) => {
			let resolved = false;
			to.on("wantTrailers", () => {
				logger.debug(`-> Request ${to.id} ready to receive trailers`);
				resolved = true;
				resolve(to);
			});
			to.on("close", () => {
				// If target stream closes before wanting trailers, close source stream as well,
				// since there's nowhere to pipe its response to now.
				if (!resolved) {
					logger.debug(`Request ${to.id} closed, dropping subrequest ${from.id} trailers`);
					from.end();
					reject(new Error("Target stream closed before wanting trailers"));
				}
			});
		}),
	]);

	logger.debug(`-> Forwarding trailers from subrequest ${from.id} to request ${to.id} `);
	toAwaitingTrailers.sendTrailers(trailers);
}
