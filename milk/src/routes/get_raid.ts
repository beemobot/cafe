import {FastifyInstance} from "fastify";
import {route$GetRaidAsJson} from "./get_raid/get_raid_as_json.js";
import {route$GetRaidAsText} from "./get_raid/get_raid_as_text.js";
import {route$GetAntispam} from "./get_raid/get_antispam.js";

export type RaidParameter = {
    Params: { id: string }
}

export type CursoredRaidParameter = {
    Params: { id: string },
    Querystring: { cursor: string | null }
}

export default async (fastify: FastifyInstance) => {
    fastify.get<RaidParameter>('/antispam/:id', route$GetAntispam)
    fastify.get<RaidParameter>('/raid/:id', route$GetRaidAsText)
    fastify.get<CursoredRaidParameter>('/raid/:id.json', route$GetRaidAsJson)
}