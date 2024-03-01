import {Record, Static, String} from "runtypes";

const PremiumManagementData = Record({
    guildId: String,
    premiumPlan: String
})

export type PremiumManagementData = Static<typeof PremiumManagementData>