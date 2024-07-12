import {Logger} from "@beemobot/common";
// ^ This needs to be updated; Probably @beemobot/cafe
import Fastify from "fastify";
import GetAntispam from "../routes/get_raid.js";
import LogHook from "../hooks/log_hook.js";
import ErrorHook from "../hooks/error_hook.js";
import DefaultRoute from "../routes/default_route.js";
import {logError, logIssue} from "./sentry.js";
import {TAG} from "../constants/logging.js";

const server = Fastify.default({
    ignoreTrailingSlash: true,
    ignoreDuplicateSlashes: true,
    trustProxy: (process.env.TRUST_PROXY ?? 'false').toLowerCase() === 'true',
    disableRequestLogging: true
})

export async function initializeFastify() {
    try {
        if (!process.env.SERVER_PORT || Number.isNaN(process.env.SERVER_PORT)) {
            logIssue('You need to configure a server port for the service to work.')
            return
        }

        for (const attachable of [ErrorHook, LogHook, GetAntispam, DefaultRoute]) {
            attachable(server)
        }

        const port = Number.parseInt(process.env.SERVER_PORT)
        await server.listen({
            port: port,
            host: '0.0.0.0'
        })

        Logger.info(TAG, `Milk is now serving logs under port ${port}.`)
    } catch (ex) {
        logError('An issue occurred while trying to start Fastify.', ex)
    }
}