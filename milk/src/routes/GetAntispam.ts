import {prisma, TAG} from "../index.js";
import * as Sentry from "@sentry/node";
import {Logger} from "@beemobot/common";
// ^ This needs to be updated; Probably @beemobot/cafe
import {DateUtil} from "../utils/date.js";
import {AntispamLogsCache} from "../cache/antispamLogsCache.js";
import {FastifyInstance} from "fastify";

const attach = (server: FastifyInstance) => {
    server.get('/antispam', (request, reply) => reply.send('You came to the wrong spot, buddy!'))
    server.get<{Params:{ id: string}}>('/antispam/:id', async (request, reply) => {
        try {
            const { id } = request.params
            const cache = AntispamLogsCache.get<string>(id)

            if (cache != null) {
                return reply.send(cache)
            }

            const raid = await prisma.raid.findUnique({ where: { external_id: id } })

            if (raid == null) {
                return reply.code(404).send('404 Not Found')
            }

            const users = await prisma.raidUser.findMany({ where: { internal_raid_id: raid.internal_id } })

            // IMPORTANT: Impossible situation unless Tea sent milk an empty list for raid users which is weird.
            if (users.length === 0) {
                Sentry.captureException(new Error("Raid " + id + " has zero users on collect, impossible situation."))
                Logger.error(TAG, 'Raid ' + id + ' has zero users on collect, this should not be possible!')

                return reply.code(404).send('404 Not Found')
            }

            let response = 'Userbot raid detected against server ' + raid.guild_id + ' on ' + DateUtil.toDateString(users[0].joined_at);
            response += '\nRaid size: ' + users.length + ' accounts'
            response += '\n'
            response += '\n   Joined at:              ID:             Username:'
            response += '\n'

            let userIds = '';
            for (const user of users) {
                response += DateUtil.toTimeString(user.joined_at) + '   ' + user.user_id + '  ' + user.name
                userIds += user.user_id
            }

            response += '\n'
            response += '\n     Raw IDs:'
            response += '\n'
            response += userIds

            AntispamLogsCache.set(id, response)
            return reply.send(response)
        } catch (ex) {
            console.error(ex)
        }
    })
}

export const GetAntispam = { attach: attach }