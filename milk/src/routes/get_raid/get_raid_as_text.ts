import {FastifyReply, FastifyRequest} from "fastify";
import {useCacheWhenPossible} from "../../fastify/serve_cached.js";
import {getRaid} from "../../database/raid.js";
import {getPublicRaidUsers} from "../../database/raid_users.js";
import {toDateString, toTimeString} from "../../utils/date.js";
import {Logger} from "@beemobot/common";
import {TAG} from "../../constants/logging.js";
import {RaidParameter} from "../get_raid.js";

export async function route$GetRaidAsText(request: FastifyRequest<RaidParameter>, reply: FastifyReply): Promise<FastifyReply> {
    let { id } = request.params
    return await useCacheWhenPossible(reply, id, async () => {
        const raid = await getRaid(id)

        if (raid == null) {
            return reply.code(404).send('404 Not Found')
        }

        const users = await getPublicRaidUsers(raid.internal_id)

        let response: string
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
        return {result: response, shouldCache: users.length !== 0}
    })
}