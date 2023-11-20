import {KafkaConnection, Logger} from "@beemobot/common";
// ^ This needs to be updated; Probably @beemobot/cafe
import {initKafkaClients} from "../kafka/clients.js";
import {logIssue} from "./sentry.js";
import {TAG} from "../constants/logging.js";

export let kafka: KafkaConnection

export async function initializeKafka() {
    process.env.KAFKAJS_NO_PARTITIONER_WARNING = "1"

    if (!process.env.KAFKA_HOST) {
        logIssue('Kafka is needed to start this service. If you need to run this for read-only, ' +
            'please properly configure that on the configuration.')
        return
    }

    Logger.info(TAG, "Attempting to connect to Kafka " + JSON.stringify({ host: process.env.KAFKA_HOST }))

    kafka = new KafkaConnection(process.env.KAFKA_HOST, "milk", "milk", "-5")
    initKafkaClients(kafka)

    await kafka.start()
}