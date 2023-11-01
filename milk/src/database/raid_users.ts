import {prisma} from "../connections/prisma.js";
import {PublicRaidUser} from "../types/raid.js";
import {RaidUser} from "@prisma/client";

/**
 * Gets the users involved in a {@link Raid}.
 *
 * @param internalId the internal id
 * @return the users involved in a {@link Raid}.
 */
export const getRaidUsers = async (internalId: string): Promise<RaidUser[]> =>
    (await prisma.raidUser.findMany({where: {internal_raid_id: internalId}}))

/**
 * Gets the users involved in a {@link Raid} as a {@link PublicRaidUser} type which does not include the
 * `internal_raid_id`.
 *
 * @param internalId the internal id
 * @return the users involved in a {@link Raid}.
 */
export const getPublicRaidUsers = async (internalId: string): Promise<PublicRaidUser[]> => (await getRaidUsers(internalId))
    .map(user => {
        return {
            id: user.user_id.toString(),
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