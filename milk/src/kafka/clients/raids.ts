import {BrokerClient, KafkaConnection, Logger} from "@beemobot/common";
// ^ This needs to be updated; Probably @beemobot/cafe
import {prisma, TAG} from "../../index.js";
import {randomString} from "../../utils/string.js";
import {retriable} from "../../utils/retriable.js";
import {RaidManagementData} from "../../types/raid.js";

export const KEY_BATCH_INSERT_RAID_USERS = "batch-insert-raid-users"
export class RaidManagementClient extends BrokerClient<RaidManagementData> {
    constructor(conn: KafkaConnection) {
        super(conn, "raid-management");
        this.on(KEY_BATCH_INSERT_RAID_USERS, async (message) => {
            if (message.value == null || message.value.request == null) {
                Logger.warn(TAG, `Received a message on ${KEY_BATCH_INSERT_RAID_USERS} but no request details was found.`)
                return
            }

            const request = message.value.request

            if (request.users.length > 0) {
                Logger.info(TAG, `Inserting ${request.users.length} users to the raid ${request.raidId}.`)
                const users = request.users.map((user) =>  {
                    return {
                        internal_raid_id: request.raidId,
                        user_id: BigInt(user.idString),
                        name: user.name,
                        avatar_hash: user.avatarHash,
                        created_at: user.createdAt,
                        joined_at: user.joinedAt
                    }
                })

                await retriable(
                    'insert_raid_users',
                    async () => prisma.raidUser.createMany({data: users, skipDuplicates: true}),
                    2,
                    25
                )
            }

            let raid = await prisma.raid.findUnique({where: {internal_id: request.raidId}})
            if (raid == null) {
                Logger.info(TAG, `Creating raid ${request.raidId} from guild ${request.guildIdString}.`)
                raid = await retriable(
                    'create_raid',
                    async () => prisma.raid.create({
                        data: {
                            internal_id: request.raidId,
                            external_id: randomString(12),
                            guild_id: BigInt(request.guildIdString),
                            concluded_at: request.concludedAt
                        }
                    }),
                    1,
                    25
                )
            } else {
                if (request.concludedAt != null && (raid.concluded_at == null || raid.concluded_at !== new Date(request.concludedAt))) {
                    Logger.info(TAG, `Concluding raid ${request.raidId} from guild ${request.guildIdString}.`)
                    raid = await retriable(
                        'conclude_raid',
                        async () => prisma.raid.update({
                            where: { external_id: raid!.external_id, internal_id: request.raidId },
                            data: { concluded_at: request.concludedAt }
                        }),
                        0.2,
                        25
                    )
                }
            }

            await message.respond({ response: { externalId: raid!.external_id }, request: null })
        })
    }
}