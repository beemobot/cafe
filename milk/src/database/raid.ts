import {prisma} from "../connections/prisma.js";
import {Raid} from "@prisma/client";

/**
 * Gets information about a given {@link Raid}.
 * @param externalId the external id of the raid.
 * @return {@link Raid} or null if it doesn't exist.
 */
export const getRaid = async (externalId: string): Promise<Raid | null> =>
    (await prisma.raid.findUnique({where: {external_id: externalId}}))