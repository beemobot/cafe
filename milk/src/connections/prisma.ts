// ^ This needs to be updated; Probably @beemobot/cafe
import {prisma} from "../index.js";
import {logError, logIssue} from "./sentry.js";

export async function initializePrisma() {
    try {
        if (process.env.DATABASE_URL == null) {
            logIssue('No database URI has been found on the configuration. Please configure it as the service cannot run without it.')
            return
        }

        await prisma.$connect()
    } catch (ex) {
        logError('Failed to connect to the database, closing service.', ex)
        process.exit()
    }
}