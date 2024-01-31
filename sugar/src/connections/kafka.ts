import {KafkaConnection, Logger} from "@beemobot/common";
// ^ This needs to be updated; Probably @beemobot/cafe

import {TAG} from "../constants/logging.js";
import {initKafkaClients} from "../kafka/kafkaClients.js";

export let kafka: KafkaConnection

export async function initKafka() {
    process.env.KAFKAJS_NO_PARTITIONER_WARNING = "1"
    if (process.env.KAFKA_HOST == null) {
        Logger.error(TAG, 'Kafka has to be configured because it is a required module.')
        process.exit()
    }

    kafka = new KafkaConnection(process.env.KAFKA_HOST, "sugar", "sugar", "-2")
    initKafkaClients(kafka)
    await kafka.start()
}