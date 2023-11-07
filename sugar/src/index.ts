import dotenv from 'dotenv'
import {initSentry} from "./connections/sentry.js";
import {initChargebee} from "./connections/chargebee.js";
import {initDiscordWebhookClient} from "./connections/discord.js";
import {initKafka} from "./connections/kafka.js";
import {initExpressServer} from "./connections/express.js";


(async () => {
    try {
        dotenv.config()

        initSentry()
        initChargebee()
        initDiscordWebhookClient()
        await initKafka()
        initExpressServer()
    } catch (ex) {
        console.error('Failed to continue prerequisites task for startup.', ex)
    }
})()