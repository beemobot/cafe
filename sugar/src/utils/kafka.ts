import {Server} from "../types/server.js";
import {KafkaMessageHeaders} from "@beemobot/water";

export function createPremiumManagementData(server: Server, plan: string) {
    return '{"guildId":' + server.id +',"premiumPlan":"'+plan+'"}'
}

export function createRecordHeaders() {
    return new KafkaMessageHeaders('sugar-sugar', '-2')
}