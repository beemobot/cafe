import * as Sentry from '@sentry/node'
import {Logger} from "@beemobot/common";
// ^ This needs to be updated; Probably @beemobot/cafe

import {TAG} from "../constants/logging.js";
export function initSentry() {
    if (process.env.SENTRY_DSN == null) {
        Logger.error(TAG, 'Sentry must be configured for proper error handling.')
        process.exit()
    }

    Sentry.init({ dsn: process.env.SENTRY_DSN, tracesSampleRate: 1.0,  })
}