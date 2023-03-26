import {Server} from "../types/server.js";
import {KafkaMessageHeaders} from "@beemobot/common";
// ^ This needs to be updated; Probably @beemobot/cafe

export function createPremiumManagementData(server: Server, plan: string) {
    return '{"guildId":' + server.id +',"premiumPlan":"'+plan+'"}'
}

export function createRecordHeaders() {
    return new KafkaMessageHeaders('sugar-sugar', '-2')
}