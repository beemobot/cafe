import {PrismaClient} from "@prisma/client";
import dotenv from 'dotenv'
import {Sentryboo} from "./connections/sentry.js";
import {Koffaka} from "./connections/kafka.js";
import {Prismae} from "./connections/prisma.js";
import {Logger} from "@beemobot/water";
import {Fastified} from "./connections/fastify.js";
import * as Sentry from '@sentry/node'

dotenv.config()

export let prisma = new PrismaClient()
export const TAG = "Milk"
export const CONFIGURATION = {
    WRITES_ENABLED: process.env.WRITES_ENABLED?.toLowerCase() === 'true',
    READS_ENABLED: process.env.READS_ENABLED?.toLowerCase() === 'true'
}
async function main() {
    Sentryboo.init()
    await Prismae.init()

    Logger.info(TAG, 'Starting milk under the following conditions ' + JSON.stringify(CONFIGURATION))
    if (CONFIGURATION.WRITES_ENABLED) {
        await Koffaka.init()
    }

    if (CONFIGURATION.READS_ENABLED) {
        await Fastified.init()
    }
}

main()
    .then(() => prisma.$disconnect())
    .catch(async (ex) => {
        console.error(ex)
        Sentry.captureException(ex)

        await prisma.$disconnect()
    })