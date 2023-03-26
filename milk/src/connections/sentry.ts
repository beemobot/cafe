import * as Sentry from '@sentry/node'
import {Logger} from "@beemobot/common";
// ^ This needs to be updated; Probably @beemobot/cafe
import {TAG} from "../index.js";
function init() {
    if (process.env.SENTRY_DSN == null) {
        Logger.error(TAG, 'Sentry is not configured, discarding request to start.')
        process.exit()
        return
    }

    Sentry.init({ dsn: process.env.SENTRY_DSN, tracesSampleRate: 1.0,  })
}

export const Sentryboo = { init: init }