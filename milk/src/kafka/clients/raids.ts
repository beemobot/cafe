import {BrokerClient, KafkaConnection} from "@beemobot/common";
// ^ This needs to be updated; Probably @beemobot/cafe
import {prisma} from "../../index.js";
import {StringUtil} from "../../utils/string.js";
import {retriable} from "../../utils/retriable.js";

export const KEY_BATCH_INSERT_RAID_USERS = "batch-insert-raid-users"
export class RaidManagementClient extends BrokerClient<RaidManagementData> {
    constructor(conn: KafkaConnection) {
        super(conn, "raid-management");
        this.on(KEY_BATCH_INSERT_RAID_USERS, async (m) => {
            if (m.value == null) {
                return
            }

            const request = m.value.request
            if (request == null) {
                return
            }

            if (request.users.length > 0) {
                await retriable(
                    'insert_raid_users',
                    async () => {
                        prisma.raidUser.createMany({
                            data: request.users.map((user) =>  {
                                return {
                                    internal_raid_id: request.raidId,
                                    user_id: BigInt(user.idString),
                                    name: user.name,
                                    avatar_hash: user.avatarHash,
                                    created_at: user.createdAt,
                                    joined_at: user.joinedAt
                                }
                            }),
                            skipDuplicates: true
                        })
                    },
                    2,
                    25
                )
            }

            let raid = await prisma.raid.findUnique({where: {internal_id: request.raidId}}).then((result) => result);
            if (raid == null) {
                raid = await retriable(
                    'create_raid',
                    async () => {
                        const uuid = StringUtil.random(12)
                        return prisma.raid.create({
                            data: {
                                internal_id: request.raidId,
                                external_id: uuid,
                                guild_id: BigInt(request.guildIdString),
                                concluded_at: request.concluded_at
                            }
                        });
                    },
                    1,
                    25
                )
            } else {
                if (request.concluded_at != null && (raid.concluded_at == null || raid.concluded_at !== new Date(request.concluded_at))) {
                    raid = await retriable(
                        'conclude_raid',
                        async () => {
                            return prisma.raid.update({
                                where: { external_id: raid!.external_id, internal_id: request.raidId },
                                data: { concluded_at: request.concluded_at }
                            })
                        },
                        0.2,
                        25
                    )
                }
            }

            await m.respond({ response: { externalId: raid!.external_id }, request: null })
        })
    }
}
export type RaidManagementData = {
    request: RaidManagementRequest | null,
    response: RaidManagementResponse | null
}
export type RaidManagementRequest = {
    raidId: string,
    guildIdString: string,
    users: RaidManagementUser[],
    concluded_at: (Date | string) | null
}
export type RaidManagementResponse = {
    externalId: string
}
export type RaidManagementUser = {
    idString: string,
    name: string,
    avatarHash: string | null,
    createdAt: Date | string,
    joinedAt: Date | string
}