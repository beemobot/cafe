import {TAG} from "../constants/logging.js";
import * as Sentry from '@sentry/node';
import {Logger} from "@beemobot/common";
import {ChargebeeCustomer, ChargebeeSubscription} from "../types/chargebee.js";

export async function run<T>(
    taskName: string,
    action: () => Promise<T>,
    retryEverySeconds: number = 2,
    maxRetries: number = 25,
    retries: number = 1
): Promise<T> {
    try {
        return await action();
    } catch (exception) {
        // Raise exception once to Sentry, we don't want to possibly send so many exceptions
        if (retries === 1) {
            Sentry.captureException(exception)
        }

        if (maxRetries !== -1 && retries >= maxRetries) {
            throw exception
        }

        const secondsTillRetry = (retryEverySeconds * retries)
        Logger.error(TAG, `Failed to complete ${taskName}. Retrying in ${secondsTillRetry} seconds.`, exception)

        await new Promise((resolve) => setTimeout(resolve, secondsTillRetry * 1000))
        return run(taskName, action, retryEverySeconds, retries + 1)
    }
}

export function createTaskName(subscription: ChargebeeSubscription, customer: ChargebeeCustomer) {
    let task = 'Activation';
    if (subscription.status !== 'active') {
        task = 'Cancellation'
    }

    return task + ' ' + JSON.stringify({ server: subscription.cf_discord_server_id, plan: subscription.plan_id,
        user: customer.cf_discord_id_dont_know_disgdfindmyid })
}