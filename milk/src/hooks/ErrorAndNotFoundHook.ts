import {FastifyInstance} from "fastify";
import * as Sentry from "@sentry/node";

const attach = (server: FastifyInstance) => server
    .setNotFoundHandler((_, reply) => {  reply.code(404).send('404 Not Found') })
    .setErrorHandler((error, request, reply) => {
        if (reply.statusCode === 429) {
            reply.send('You are sending too many requests, slow down!')
            return
        }

        Sentry.captureException(error)
        reply.code(500).send('An error occurred on the server-side, the hive has been notified.')
    })

export const ErrorAndNotFoundHook = { attach: attach }