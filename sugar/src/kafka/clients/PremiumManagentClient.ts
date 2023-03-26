import {BrokerClient, IBrokerConnection} from "@beemobot/common";
// ^ This needs to be updated; Probably @beemobot/cafe
import {Record, Static, String} from "runtypes";

export const KEY_SET_PREMIUM_PLAN = "set-premium-plan"
export const PREMIUM_PLAN_TOPIC = "premium-management"
export class PremiumManagentClient extends BrokerClient<PremiumManagementData> {
    public constructor(connection: IBrokerConnection) {
        super(connection, "premium-management")
    }
}

const PremiumManagementData = Record({
    guildId: String,
    premiumPlan: String
})

export type PremiumManagementData = Static<typeof PremiumManagementData>