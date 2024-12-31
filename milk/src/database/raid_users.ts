import {prisma} from "../connections/prisma.js";
import {PublicRaidUser} from "../types/raid.js";
import {Raid, RaidUser} from "@prisma/client";

const transformToPublicRaidUser = (user: RaidUser): PublicRaidUser => {
    return {
        id: user.id.toString(),
        name: user.name,
        joinedAt: user.joined_at,
        createdAt: user.created_at,
        avatarHash: user.avatar_hash
    } satisfies PublicRaidUser
}

/**
 * Gets the users involved in a {@link Raid} as a {@link PublicRaidUser} type which does not include the
 * `internal_raid_id`.
 *
 * @param raid the raid to get the data from
 * @return the users involved in a {@link Raid}.
 */
export const getPublicRaidUsers = (raid: { users: RaidUser[] } & Raid): PublicRaidUser[] => raid.users
    .map(transformToPublicRaidUser)

/**
 * Paginates over the {@link RaidUser} associated in a given {@link Raid}.
 *
 * @param raidId the raid id
 * @param limit  the maximum users to return
 * @param cursor the cursor (joined_at) to use.
 * @return all the users associated in a {@link Raid} given a cursor.
 */
export const paginateUsers = async (raidId: string, limit: number, cursor: Date | null): Promise<{ users: PublicRaidUser[], count: number }> => {
    const results = await prisma.$transaction([
        prisma.raidUser.findMany({
            where: {
                raid_id:  raidId,
                joined_at: cursor == null ? undefined : {
                    gt: cursor
                }
            },
            take: limit,
            orderBy: {
                joined_at: 'asc'
            }
        }),
        prisma.raidUser.count({
            where: {
                raid_id: raidId
            }
        })
    ])
    return { users: results[0].map(transformToPublicRaidUser), count: results[1] }
}

/**
 * Inserts many {@link RaidUser} to the database.
 * @param users the users to insert into the database.
 */
export const insertRaidUsers = async (users: RaidUser[]) =>
    (await prisma.raidUser.createMany({data: users, skipDuplicates: true}))