import {KafkaConnection, Logger} from "@beemobot/common";
// ^ This needs to be updated; Probably @beemobot/cafe
import {TAG} from "../index.js";
import {KafkaClients} from "../kafka/clients.js";

export let kafka: KafkaConnection

export async function initializeKafka() {
    process.env.KAFKAJS_NO_PARTITIONER_WARNING = "1"
    if (!process.env.KAFKA_HOST) {
        Logger.error(TAG,
            'Kafka is needed to start this service. If you need to run this for read-only, ' +
            'please properly configure that on the configuration.'
        )
        return
    }

    Logger.info(TAG, "Attempting to connect to Kafka " + JSON.stringify({ host: process.env.KAFKA_HOST }))
    kafka = new KafkaConnection(process.env.KAFKA_HOST, "milk", "milk", "-5")
    await kafka.start()

    KafkaClients.init(kafka)
}