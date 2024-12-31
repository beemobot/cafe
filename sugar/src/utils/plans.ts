import {ChargebeeCustomer, ChargebeeSubscription} from "../types/chargebee.js";
import plans from "../../configs/plans.json";
import {Server} from "../types/server.js";
import {Logger} from "@beemobot/common";
// ^ This needs to be updated; Probably @beemobot/cafe
import {kafka} from "../connections/kafka.js";
import {createPremiumManagementData, createRecordHeaders} from "./kafka.js";
import {NO_PLAN} from "../constants/plans.js";
import {TAG} from "../constants/logging.js";
import {KEY_SET_PREMIUM_PLAN, PREMIUM_PLAN_TOPIC} from "../constants/kafka.js";
import {sendWebhook} from "./discord/webhooks.js";

export function determinePlan(subscription: ChargebeeSubscription): string {
    if (subscription.status !== 'active') return NO_PLAN
    for (const plan of plans.plans) {
        if (plan.ids.includes(subscription.plan_id)) {
            return plan.name
        }
    }

    return NO_PLAN
}

export async function updatePlan(server: Server, plan: string, subscription: ChargebeeSubscription, customer: ChargebeeCustomer) {
    Logger.info(TAG, 'Sending UPDATE PLAN for server (' + server.id + ") with new plan (" + plan + ').')
    await kafka.send(PREMIUM_PLAN_TOPIC, KEY_SET_PREMIUM_PLAN, createPremiumManagementData(server, plan), createRecordHeaders())
    Logger.info(TAG, 'Kafka has accepted UPDATE PLAN for server (' + server.id + ") with new plan (" + plan + ').')
    sendWebhook(server, plan, subscription, customer)
}