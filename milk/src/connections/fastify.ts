import {Logger} from "@beemobot/common";
// ^ This needs to be updated; Probably @beemobot/cafe
import {TAG} from "../index.js";
import Fastify, {FastifyInstance} from "fastify";
import {GetAntispam} from "../routes/GetAntispam.js";
import {LogHook} from "../hooks/LogHook.js";
import {ErrorAndNotFoundHook} from "../hooks/ErrorAndNotFoundHook.js";
import {DefaultRoute} from "../routes/DefaultRoute.js";
import {Attachable} from "../types/fastify.js";

export const server: FastifyInstance = Fastify.default({ ignoreTrailingSlash: true, ignoreDuplicateSlashes: true })
export const attachables: Attachable[] = [
    ErrorAndNotFoundHook,
    LogHook,
    GetAntispam,
    DefaultRoute
]

async function init() {
    if (!process.env.SERVER_PORT || Number.isNaN(process.env.SERVER_PORT)) {
        Logger.error(TAG, 'Server Port is not configured, discarding request to start.')
        process.exit()
        return
    }

    for (const attachable of attachables) {
        await attachable.attach(server)
    }

    const port = Number.parseInt(process.env.SERVER_PORT)
    const link = 'http://localhost:' + port

    await server.listen({
        port: port,
        host: '0.0.0.0'
    })

    Logger.info(TAG, 'Fastify Server is now running ' + JSON.stringify({
        port: port,
        antispam: link + '/antispam/',
        messages: link + '/messages/'
    }))
}

export const Fastified = { init: init }