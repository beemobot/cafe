import {RaidManagementClient} from "./clients/raids.js";
import {KafkaConnection} from "@beemobot/water";

export let raidManagementClient: RaidManagementClient
function init(connection: KafkaConnection) {
    raidManagementClient = new RaidManagementClient(connection)
}

export const KafkaClients = { init: init }