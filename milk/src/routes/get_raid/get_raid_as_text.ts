import {FastifyReply, FastifyRequest} from "fastify";
import {useCacheWhenPossible} from "../../fastify/serve_cached.js";
import {getRaidByPublicId} from "../../database/raid.js";
import {toDateString, toTimeString} from "../../utils/date.js";
import {Logger} from "@beemobot/common";
import {TAG} from "../../constants/logging.js";
import {RaidParameter} from "../get_raid.js";
import {paginateUsers} from "../../database/raid_users.js";
import {JSON_API_DOCUMENTATIONS} from "../../constants/links.js";

const numberFormatter = new Intl.NumberFormat('en-US')

export async function route$GetRaidAsText(request: FastifyRequest<RaidParameter>, reply: FastifyReply): Promise<FastifyReply> {
    let { id } = request.params
    return await useCacheWhenPossible(reply, id, 'text', async (discard) => {
        const raid = await getRaidByPublicId(id)

        if (raid == null) {
            reply.code(404).send('404 Not Found')
            return discard
        }

        const { users, count } = await paginateUsers(raid.id, 2_000, null)
        let response: string
        let startedDate = toDateString(raid.created_at)

        response = 'Userbot raid detected against server ' + raid.guild_id + ' on ' + startedDate;

        if (users.length === 0) {
            Logger.warn(TAG, `Raid ${id} reported no users.`)
            response += "\nThere are no users logged for this raid, at this moment. It is likely that the raid is still being processed, please come back later!"
        } else {
            response += '\nRaid size: ' + numberFormatter.format(count) + ' accounts'
            if (count > 2_000) {
                response += `\nTo view beyond 2,000 accounts, see ${JSON_API_DOCUMENTATIONS} for more information.`
            }
            response += '\n'
            response += '\n   Joined at:              ID:             Username:'
            response += '\n'
            let userIds = '';
            for (const user of users) {
                response += '\n'
                response += toTimeString(user.joinedAt) + "   " + user.id + spaces((18 - user.id.toString().length + 3)) + user.name

                userIds += '\n'
                userIds += user.id
            }

            response += '\n'
            response += '\n     Raw IDs:'
            response += '\n'
            response += userIds
        }
        return {
            result: response,
            shouldCache: raid.concluded_at != null || users.length > 2_000
        }
    })
}

function spaces(num: number): string {
    let whitespace = ""
    while (whitespace.length < num) {
        whitespace += " "
    }
    return whitespace
}