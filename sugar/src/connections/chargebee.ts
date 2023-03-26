import {ChargeBee} from "chargebee-typescript";
import {Logger} from "@beemobot/common";
// ^ This needs to be updated; Probably @beemobot/cafe
import {TAG} from "../index.js";

export let chargebee = new ChargeBee();

function init() {
    if (process.env.CHARGEBEE_SITE == null || process.env.CHARGEBEE_KEY == null) {
        Logger.error(TAG, 'Chargebee is not configured, discarding request to start.')
        process.exit()
        return
    }
    chargebee.configure({ site: process.env.CHARGEBEE_SITE, api_key: process.env.CHARGEBEE_KEY })
}

export const Chargebee = { init: init }