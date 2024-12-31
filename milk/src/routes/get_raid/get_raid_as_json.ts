import {FastifyReply, FastifyRequest} from "fastify";
import {useCacheWhenPossible} from "../../fastify/serve_cached.js";
import {getRaidByPublicId} from "../../database/raid.js";
import {CursoredRaidParameter} from "../get_raid.js";
import {paginateUsers} from "../../database/raid_users.js";
import {PAGINATION_LIMIT} from "../../constants/pagination.js";

export async function route$GetRaidAsJson(request: FastifyRequest<CursoredRaidParameter>, reply: FastifyReply): Promise<FastifyReply> {
    let { id } = request.params

    let cursorQuery = request.query.cursor
    let cursor: Date | null = null

    if (cursorQuery != null) {
        try {
            cursor = new Date(cursorQuery)
        } catch (ex) {
            reply.code(400).send()
        }
    }

    const cacheKey = `${id}.json$cursor=${cursor?.toISOString() ?? 'null'}`
    return await useCacheWhenPossible(reply, cacheKey, 'application/json', async (discard) => {
        const raid = await getRaidByPublicId(id)

        if (raid == null) {
            reply.code(404).send('404 Not Found')
            return discard
        }

        const { users, count } = await paginateUsers(raid.id, PAGINATION_LIMIT, cursor)
        return {
            result: JSON.stringify({
                raid: {
                    startedAt: raid.created_at,
                    concludedAt: raid.concluded_at,
                    guild: raid.guild_id.toString(),
                    size: count
                },
                users: {
                    next: users.at(users.length - 1)?.joinedAt ?? null,
                    size: users.length,
                    data: users
                },
            }),
            shouldCache: users.length >= PAGINATION_LIMIT
        }
    })
}