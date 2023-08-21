import {Logger} from "@beemobot/water";
import {prisma, TAG} from "../index.js";
async function init() {
    try {
        if (process.env.DATABASE_URL == null) {
            Logger.error(TAG, 'Prisma is not configured, discarding request to start.')
            process.exit()
            return
        }

        await prisma.$connect()
    } catch (ex) {
        Logger.error(TAG, 'Failed to connect to Prisma, closing startup.')
        console.error(ex)
        process.exit()
    }
}

export const Prismae = { init: init }