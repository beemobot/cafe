import {ChargeBee} from "chargebee-typescript";
import {Logger} from "@beemobot/common";
// ^ This needs to be updated; Probably @beemobot/cafe

import {TAG} from "../constants/logging.js";

export let chargebee = new ChargeBee();

export function initChargebee() {
    if (process.env.CHARGEBEE_SITE == null || process.env.CHARGEBEE_KEY == null) {
        Logger.error(TAG, 'Chargebee is not configured. Chargebee must be configured in order for key parts of the service to run.')
        process.exit()
    }
    chargebee.configure({ site: process.env.CHARGEBEE_SITE, api_key: process.env.CHARGEBEE_KEY })
}