import {prisma} from "../connections/prisma.js";
import {PublicRaidUser} from "../types/raid.js";
import {Raid, RaidUser} from "@prisma/client";

/**
 * Gets the users involved in a {@link Raid} as a {@link PublicRaidUser} type which does not include the
 * `internal_raid_id`.
 *
 * @param raid the raid to get the data from
 * @return the users involved in a {@link Raid}.
 */
export const getPublicRaidUsers = (raid: { users: RaidUser[] } & Raid): PublicRaidUser[] => raid.users.map(user => {
    return {
        id: user.id.toString(),
        name: user.name,
        joinedAt: user.joined_at,
        createdAt: user.created_at,
        avatarHash: user.avatar_hash
    } satisfies PublicRaidUser
})

/**
 * Inserts many {@link RaidUser} to the database.
 * @param users the users to insert into the database.
 */
export const insertRaidUsers = async (users: RaidUser[]) =>
    (await prisma.raidUser.createMany({data: users, skipDuplicates: true}))