import {RaidManagementClient} from "./clients/raids.js";
import {KafkaConnection} from "@beemobot/common";
// ^ This needs to be updated; Probably @beemobot/cafe

export let raidManagementClient: RaidManagementClient
export function initKafkaClients(connection: KafkaConnection) {
    raidManagementClient = new RaidManagementClient(connection)
}