import {prisma, TAG} from "../index.js";
import {Logger} from "@beemobot/common";
// ^ This needs to be updated; Probably @beemobot/cafe
import {toDateString, toTimeString} from "../utils/date.js";
import {FastifyInstance} from "fastify";
import NodeCache from "node-cache";

const logsCache = new NodeCache({ stdTTL: 10 * 1000 * 60 })

export default async (fastify: FastifyInstance) => {
    fastify.get('/antispam', (_, reply) => reply.send('You came to the wrong spot, buddy!'))
    fastify.get<{Params:{ id: string}}>('/antispam/:id', async (request, reply) => {
        try {
            const { id } = request.params
            const isJsonContentType = request.headers["content-type"] === "application/json"

            const cacheKey = isJsonContentType ? id + ".json" : id
            const cache = logsCache.get<string>(cacheKey)

            if (cache != null) {
                return reply.send(cache)
            }

            const raid = await prisma.raid.findUnique({ where: { external_id: id } })

            if (raid == null) {
                return reply.code(404).send('404 Not Found')
            }

            const users = await prisma.raidUser.findMany({ where: { internal_raid_id: raid.internal_id } })

            if (!isJsonContentType) {
                let startedDate = "N/A"
                if (users.length > 0) {
                    startedDate = toDateString(users[0].joined_at)
                }

                let response = 'Userbot raid detected against server ' + raid.guild_id + ' on ' + startedDate;
                if (users.length === 0) {
                    Logger.warn(TAG, "Raid " + id + "  reported no users.")
                    response += "\nThere are no users logged for this raid, at this moment. It is likely that the raid is still being processed, please come back later!"
                } else {
                    response += '\nRaid size: ' + users.length + ' accounts'
                    response += '\n'
                    response += '\n   Joined at:              ID:             Username:'
                    response += '\n'
                    let userIds = '';
                    for (const user of users) {
                        response += toTimeString(user.joined_at) + '   ' + user.user_id + '  ' + user.name
                        userIds += user.user_id
                    }

                    response += '\n'
                    response += '\n     Raw IDs:'
                    response += '\n'
                    response += userIds
                    logsCache.set(cacheKey, response)
                }
                return reply.send(response)
            }

            let response = JSON.stringify({
                size: users.length,
                started_at: users[0]?.joined_at,
                concluded_at: raid.concluded_at,
                guild: raid.guild_id,
                accounts: users
            })

            logsCache.set(cacheKey, response)
            return reply.send(response)
        } catch (ex) {
            console.error(ex)
        }
    })
}