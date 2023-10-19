import {Logger} from "@beemobot/common";
// ^ This needs to be updated; Probably @beemobot/cafe
import {prisma, TAG} from "../index.js";
export async function initializePrisma() {
    try {
        if (process.env.DATABASE_URL == null) {
            Logger.error(TAG, 'No database URI has been found on the configuration. Please configure it as the service cannot run without it.')
            return
        }

        await prisma.$connect()
    } catch (ex) {
        Logger.error(TAG, 'Failed to connect to the database, closing service.')
        console.error(ex)
        process.exit()
    }
}