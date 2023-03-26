import {Logger} from "@beemobot/common";
// ^ This needs to be updated; Probably @beemobot/cafe
import {TAG} from "../index.js";
import {ChargebeeCustomer, ChargebeeSubscription} from "../types/chargebee.js";
import * as Sentry from '@sentry/node';

export function retriable(task: string, action: () => Promise<void>, retries: number = 1) {
    action()
        .catch((exception) => {
            if (retries === 1) {
                Sentry.setExtra('task', task)
                Sentry.captureException(exception)
            }

            Logger.error(TAG, 'Failed to complete ' + task + '. Retrying in ' +  (10 * retries) + ' seconds.\n', exception)
            setTimeout(() => retriable(task, action, retries + 1), (10 * retries) * 1000)
        })
}

export function createTaskName(subscription: ChargebeeSubscription, customer: ChargebeeCustomer) {
    let task = 'Activation';
    if (subscription.status !== 'active') {
        task = 'Cancellation'
    }

    return task + ' ' + JSON.stringify({ server: subscription.cf_discord_server_id, plan: subscription.plan_id,
        user: customer.cf_discord_id_dont_know_disgdfindmyid })
}