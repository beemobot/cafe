import {Server} from "../types/server.js";
import {KafkaMessageHeaders} from "@beemobot/common";
import {stringify} from "./json.js";
// ^ This needs to be updated; Probably @beemobot/cafe

export function createPremiumManagementData(server: Server, plan: string) {
    return stringify({
        guildId: BigInt(server.id),
        premiumPlan: plan
    })
}

export function createRecordHeaders() {
    return new KafkaMessageHeaders('sugar', '-2')
}