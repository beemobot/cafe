import * as Sentry from '@sentry/node';
import {Logger} from "@beemobot/common";
// ^ This needs to be updated; Probably @beemobot/cafe
import {TAG} from "../index.js";

export async function retriable<T>(task: string, action: () => Promise<T>, retryEverySeconds: number = 10, maxRetries: number = -1, retries: number = 1): Promise<T> {
    try {
        return await action();
    } catch (exception) {
        if (retries === 1) {
            Sentry.captureException(exception)
        }

        if (maxRetries !== -1 && retries >= maxRetries) {
            throw exception
        }

        Logger.error(TAG, 'Failed to complete ' + task + '. Retrying in ' + (retryEverySeconds * retries) + ' seconds.\n', exception)
        await new Promise((resolve) => setTimeout(resolve, (retryEverySeconds * retries) * 1000))
        return retriable(task, action, retryEverySeconds, retries + 1)
    }
}

