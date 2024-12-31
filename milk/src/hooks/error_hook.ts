import {FastifyInstance} from "fastify";
import * as Sentry from "@sentry/node";

export default async (fastify: FastifyInstance) => {
    fastify
        .setNotFoundHandler((_, reply) => {  reply.code(404).send('404 Not Found') })
        .setErrorHandler((error, _, reply) => {
            if (reply.statusCode === 429) {
                reply.send('You are sending too many requests, slow down!')
                return
            }

            if (process.env.SENTRY_DSN != null) {
                Sentry.captureException(error)
            }
            reply.code(500).send('An error occurred on the server-side, the hive has been notified.')
        })
}