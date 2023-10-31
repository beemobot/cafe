import {PrismaClient} from "@prisma/client";
import dotenv from 'dotenv'
import {initializeSentry, logIssue} from "./connections/sentry.js";
import {initializeKafka} from "./connections/kafka.js";
import {initializePrisma} from "./connections/prisma.js";
import {Logger} from "@beemobot/common";
// ^ This needs to be updated; Probably @beemobot/cafe
import {initializeFastify} from "./connections/fastify.js";
import * as Sentry from '@sentry/node'

dotenv.config()

export let prisma = new PrismaClient()
export const TAG = "Milk"

async function main() {
    initializeSentry()
    await initializePrisma()

    const configuration = {
        writesEnabled: process.env.WRITES_ENABLED?.toLowerCase() === 'true',
        readsEnabled: process.env.READS_ENABLED?.toLowerCase() === 'true'
    }

    Logger.info(TAG, 'Starting milk under the following conditions ' + JSON.stringify(configuration))

    if (!configuration.readsEnabled && !configuration.writesEnabled) {
        logIssue('Milk needs to be in at least read or write mode to function.')
        return
    }

    if (configuration.writesEnabled) {
        await initializeKafka()
    }

    if (configuration.readsEnabled) {
        await initializeFastify()
    }
}

main()
    .then(() => prisma.$disconnect())
    .catch(async (ex) => {
        console.error(ex)
        Sentry.captureException(ex)

        await prisma.$disconnect()
    })