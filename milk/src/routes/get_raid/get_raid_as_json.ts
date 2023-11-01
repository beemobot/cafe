import {FastifyReply, FastifyRequest} from "fastify";
import {useCacheWhenPossible} from "../../fastify/serve_cached.js";
import {getRaidByPublicId} from "../../database/raid.js";
import {RaidParameter} from "../get_raid.js";
import {getPublicRaidUsers} from "../../database/raid_users.js";

export async function route$GetRaidAsJson(request: FastifyRequest<RaidParameter>, reply: FastifyReply): Promise<FastifyReply> {
    let { id } = request.params
    return await useCacheWhenPossible(reply, `${id}.json`, async () => {
        const raid = await getRaidByPublicId(id)

        if (raid == null) {
            return reply.code(404).send('404 Not Found')
        }

        const users = getPublicRaidUsers(raid)
        return {
            result: JSON.stringify({
                size: users.length,
                startedAt: users[0]?.joinedAt,
                concludedAt: raid.concluded_at,
                guild: raid.guild_id.toString(),
                users
            }),
            shouldCache: raid.concluded_at != null || users.length > 2_000
        }
    })
}