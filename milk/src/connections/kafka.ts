import {KafkaConnection, Logger} from "@beemobot/common";
// ^ This needs to be updated; Probably @beemobot/cafe
import {TAG} from "../index.js";
import {KafkaClients} from "../kafka/clients.js";

export let kafka: KafkaConnection

async function init() {
    process.env.KAFKAJS_NO_PARTITIONER_WARNING = "1"
    if (!process.env.KAFKA_HOST) {
        Logger.error(TAG, 'Kafka is not configured, discarding request to start.')
        process.exit()
        return
    }

    Logger.info(TAG, "Attempting to start Kafka " + JSON.stringify({ host: process.env.KAFKA_HOST }))
    kafka = new KafkaConnection(process.env.KAFKA_HOST, "milk", "milk", "-5")
    await kafka.start()

    KafkaClients.init(kafka)
}

export const Koffaka = { init: init }