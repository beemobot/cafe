import {Logger} from "@beemobot/common";
// ^ This needs to be updated; Probably @beemobot/cafe
import {TAG} from "../index.js";
import {FastifyInstance} from "fastify";

const attach = (server: FastifyInstance) => server.addHook(
    'preHandler',
    async (request) => Logger.info(TAG, 'Request ' + JSON.stringify({ method: request.method, url: request.url, ip: request.ip }))
)
export const LogHook = { attach: attach }