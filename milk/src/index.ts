import {PrismaClient} from "@prisma/client";
import dotenv from 'dotenv'
import {initializeSentry} from "./connections/sentry.js";
import {initializeKafka} from "./connections/kafka.js";
import {initializePrisma} from "./connections/prisma.js";
import {Logger} from "@beemobot/common";
// ^ This needs to be updated; Probably @beemobot/cafe
import {initializeFastify} from "./connections/fastify.js";
import * as Sentry from '@sentry/node'

dotenv.config()

export let prisma = new PrismaClient()
export const TAG = "Milk"
export const CONFIGURATION = {
    WRITES_ENABLED: process.env.WRITES_ENABLED?.toLowerCase() === 'true',
    READS_ENABLED: process.env.READS_ENABLED?.toLowerCase() === 'true'
}
async function main() {
    initializeSentry()
    await initializePrisma()

    Logger.info(TAG, 'Starting milk under the following conditions ' + JSON.stringify(CONFIGURATION))
    if (CONFIGURATION.WRITES_ENABLED) {
        await initializeKafka()
    }

    if (CONFIGURATION.READS_ENABLED) {
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