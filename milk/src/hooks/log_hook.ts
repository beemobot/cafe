import {Logger} from "@beemobot/common";
// ^ This needs to be updated; Probably @beemobot/cafe
import {FastifyInstance} from "fastify";
import {TAG} from "../constants/logging.js";

export default async (fastify: FastifyInstance) => {
    fastify.addHook(
        'preHandler',
        async (request) =>
            Logger.info(TAG, 'Request ' + JSON.stringify({ method: request.method, url: request.url, ip: request.ip }))
    )
}