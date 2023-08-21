import {KafkaConnection} from "@beemobot/water";
import {PremiumManagentClient} from "./clients/PremiumManagentClient.js";

export let premiumManagementClient: PremiumManagentClient
// TODO: Add actual use for this once we transition to using Sugar as a centralized subscription handler.
// noinspection JSUnusedLocalSymbols
function init(connection: KafkaConnection) {
    premiumManagementClient = new PremiumManagentClient(connection)
}