import {prisma} from "../connections/prisma.js";
import {Raid} from "@prisma/client";
import {randomString} from "../utils/string.js";

/**
 * Given an {@link publicId}, gets information about a given {@link Raid} with the {@link RaidUser}
 * relation included.
 *
 * @param publicId the external id of the raid.
 * @return {@link Raid} or null if it doesn't exist.
 */
export const getRaidByPublicId = async (publicId: string) => {
    return (await prisma.raid.findUnique({where: {public_id: publicId}}));
}

/**
 * Given an {@link id}, gets information about a given {@link Raid}.
 * @param id the external id of the raid.
 * @return {@link Raid} or null if it doesn't exist.
 */
export const getRaidByInternalId = async (id: string): Promise<Raid | null> =>
    (await prisma.raid.findUnique({where: {id}}))

/**
 * Concludes a {@link Raid} in the database.
 *
 * @param publicId the public id of the raid.
 * @param id the internal id of the raid.
 * @param concludedAt the time when the raid was concluded.
 */
export const concludeRaid = async (publicId: string, id: string, concludedAt: Date | null) =>
    (await prisma.raid.update({
        where: { id, public_id: publicId },
        data: { concluded_at: concludedAt }
    }))

/**
 * Creates a new {@link Raid}.
 *
 * @param id  the internal id of the raid.
 * @param guildId     the id of the guild being raided.
 * @param concludedAt the conclusion time of the raid, if provided.
 */
export const createRaid = async (id: string, guildId: string, concludedAt: Date | null) => (
    await prisma.raid.create({
        data: {
            id: id,
            public_id: randomString(12),
            guild_id: BigInt(guildId),
            concluded_at: concludedAt
        }
    })
)