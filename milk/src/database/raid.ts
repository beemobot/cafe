import {prisma} from "../connections/prisma.js";
import {Raid} from "@prisma/client";
import {randomString} from "../utils/string.js";

/**
 * Given an {@link externalId}, gets information about a given {@link Raid}.
 * @param externalId the external id of the raid.
 * @return {@link Raid} or null if it doesn't exist.
 */
export const getRaidByExternalId = async (externalId: string): Promise<Raid | null> =>
    (await prisma.raid.findUnique({where: {external_id: externalId}}))

/**
 * Given an {@link internalId}, gets information about a given {@link Raid}.
 * @param internalId the external id of the raid.
 * @return {@link Raid} or null if it doesn't exist.
 */
export const getRaidByInternalId = async (internalId: string): Promise<Raid | null> =>
    (await prisma.raid.findUnique({where: {internal_id: internalId}}))

/**
 * Concludes a {@link Raid} in the database.
 *
 * @param externalId the external id of the raid.
 * @param internalId the internal id of the raid.
 * @param concludedAt the time when the raid was concluded.
 */
export const concludeRaid = async (externalId: string, internalId: string, concludedAt: Date | null) =>
    (await prisma.raid.update({
        where: { external_id: externalId, internal_id: internalId },
        data: { concluded_at: concludedAt }
    }))

/**
 * Creates a new {@link Raid}.
 *
 * @param internalId  the internal id of the raid.
 * @param guildId     the id of the guild being raided.
 * @param concludedAt the conclusion time of the raid, if provided.
 */
export const createRaid = async (internalId: string, guildId: string, concludedAt: Date | null) => (
    prisma.raid.create({
        data: {
            internal_id: internalId,
            external_id: randomString(12),
            guild_id: BigInt(guildId),
            concluded_at: concludedAt
        }
    })
)