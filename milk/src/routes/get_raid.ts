import {prisma, TAG} from "../index.js";
import {Logger} from "@beemobot/common";
// ^ This needs to be updated; Probably @beemobot/cafe
import {toDateString, toTimeString} from "../utils/date.js";
import {FastifyInstance} from "fastify";
import NodeCache from "node-cache";
import {PublicRaidUser} from "../types/raid.js";

const TEN_MINUTES = 60 * 10
const logsCache = new NodeCache({ stdTTL: TEN_MINUTES })

type IdParameter = {Params:{ id: string } }
export default async (fastify: FastifyInstance) => {
    fastify.get<IdParameter>('/antispam/:id', (request, reply) => {
        let { id } = request.params
        reply.redirect('/raid/' + encodeURIComponent(id))
    })
    fastify.get<IdParameter>('/raid/:id', async (request, reply) => {
        let { id } = request.params
        const isJsonContentType = id.endsWith(".json") || request.headers.accept === "application/json"

        const cacheKey = isJsonContentType ? id + ".json" : id
        const cachedResult = logsCache.get<string>(cacheKey)
        if (cachedResult != null) {
            return reply.send(cachedResult)
        }

        if (id.includes(".")) {
            id = id.split(".")[0]
        }

        const raid = await prisma.raid.findUnique({ where: { external_id: id } })

        if (raid == null) {
            return reply.code(404).send('404 Not Found')
        }

        const users = (await prisma.raidUser.findMany({ where: { internal_raid_id: raid.internal_id } }))
            .map(user => {
                return {
                    id: user.user_id.toString(),
                    name: user.name,
                    joinedAt: user.joined_at,
                    createdAt: user.created_at,
                    avatarHash: user.avatar_hash
                } satisfies PublicRaidUser
            })

        let response: string
        const shouldCache: boolean = users.length !== 0

        if (isJsonContentType) {
            response = JSON.stringify({
                size: users.length,
                startedAt: users[0]?.joinedAt,
                concludedAt: raid.concluded_at,
                guild: raid.guild_id.toString(),
                users
            })
        } else {
            let startedDate = "N/A"
            if (users.length > 0) {
                startedDate = toDateString(users[0].joinedAt)
            }

            response = 'Userbot raid detected against server ' + raid.guild_id + ' on ' + startedDate;

            if (users.length === 0) {
                Logger.warn(TAG, `Raid ${id} reported no users.`)
                response += "\nThere are no users logged for this raid, at this moment. It is likely that the raid is still being processed, please come back later!"
            } else {
                response += '\nRaid size: ' + users.length + ' accounts'
                response += '\n'
                response += '\n   Joined at:              ID:             Username:'
                response += '\n'
                let userIds = '';
                for (const user of users) {
                    response += toTimeString(user.joinedAt) + '   ' + user.id + '  ' + user.name
                    if (userIds !== '') {
                        userIds += '\n'
                    }
                    userIds += user.id
                }

                response += '\n'
                response += '\n     Raw IDs:'
                response += '\n'
                response += userIds
            }
        }

        if (shouldCache) logsCache.set(cacheKey, response)
        return reply.send(response)
    })
}