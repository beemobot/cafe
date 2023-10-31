import * as Sentry from '@sentry/node'
import {Logger} from "@beemobot/common";
// ^ This needs to be updated; Probably @beemobot/cafe
import {TAG} from "../index.js";
export function initializeSentry() {
    if (process.env.SENTRY_DSN == null) {
        Logger.warn(TAG, 'Sentry is not configured, we recommend configuring Sentry to catch issues properly.')
        return
    }

    Sentry.init({dsn: process.env.SENTRY_DSN, tracesSampleRate: 1.0,})
}

export function logIssue(message: string) {
    const error = Error(message)

    Sentry.captureException(error)
    Logger.error(TAG, error.message)
}

export function logError(message: string, ex: any) {
    Sentry.captureException(ex)
    Logger.error(TAG, message, ex)
}
