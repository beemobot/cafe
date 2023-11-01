import {FastifyInstance} from "fastify";
import {route$GetRaidAsJson} from "./get_raid/get_raid_as_json.js";
import {route$GetRaidAsText} from "./get_raid/get_raid_as_text.js";
import {route$GetAntispam} from "./get_raid/get_antispam.js";

export type RaidParameter = {
    Params: { id: string }
}

export default async (fastify: FastifyInstance) => {
    fastify.get<RaidParameter>('/antispam/:id', route$GetAntispam)
    fastify.get<RaidParameter>('/raid/:id', route$GetRaidAsText)
    fastify.get<RaidParameter>('/raid/:id.json', route$GetRaidAsJson)
}