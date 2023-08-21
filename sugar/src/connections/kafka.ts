import {KafkaConnection, Logger} from "@beemobot/water";
import {TAG} from "../index.js";

export let kafka: KafkaConnection

async function init() {
    process.env.KAFKAJS_NO_PARTITIONER_WARNING = "1"
    if (process.env.KAFKA_HOST == null) {
        Logger.error(TAG, 'Kafka is not configured, discarding request to start.')
        process.exit()
        return
    }

    kafka = new KafkaConnection(process.env.KAFKA_HOST, "sugar-sugar", "sugar-sugar", "-2")
    await kafka.start()
}

export const Koffaka = { init: init }