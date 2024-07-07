import {KafkaConnection} from "@beemobot/common";
// ^ This needs to be updated; Probably @beemobot/cafe
import {PremiumManagentClient} from "./clients/PremiumManagentClient.js";

export let premiumManagementClient: PremiumManagentClient
// TODO: Add actual use for this once we transition to using Sugar as a centralized subscription handler.
export function initKafkaClients(connection: KafkaConnection) {
    premiumManagementClient = new PremiumManagentClient(connection)
}