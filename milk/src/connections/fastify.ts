import {Logger} from "@beemobot/common";
// ^ This needs to be updated; Probably @beemobot/cafe
import {TAG} from "../index.js";
import Fastify from "fastify";
import GetAntispam from "../routes/get_antispam.js";
import LogHook from "../hooks/log_hook.js";
import ErrorHook from "../hooks/error_hook.js";
import DefaultRoute from "../routes/default_route.js";

const server = Fastify.default({
    ignoreTrailingSlash: true,
    ignoreDuplicateSlashes: true,
    trustProxy: (process.env.TRUST_PROXY ?? 'false').toLowerCase() === 'true'
})

export async function initializeFastify() {
    if (!process.env.SERVER_PORT || Number.isNaN(process.env.SERVER_PORT)) {
        Logger.error(TAG, 'You need to configure a server port for the service to work.')
        return
    }

    server.register(fastify =>  {
        for (const attachable of [ErrorHook, LogHook, GetAntispam, DefaultRoute]) {
            attachable(fastify)
        }
    })

    const port = Number.parseInt(process.env.SERVER_PORT)
    const link = 'http://localhost:' + port

    await server.listen({
        port: port,
        host: '0.0.0.0'
    })

    Logger.info(TAG, 'Milk service is now serving. ' + JSON.stringify({
        port: port,
        antispam: link + '/antispam/',
        messages: link + '/messages/'
    }))
}