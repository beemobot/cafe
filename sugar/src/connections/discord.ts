import {Logger} from "@beemobot/common";
// ^ This needs to be updated; Probably @beemobot/cafe
import {MessagePayload, WebhookClient, WebhookMessageCreateOptions} from "discord.js";
import * as Sentry from "@sentry/node"
import {TAG} from "../constants/logging.js";

export let webhook: WebhookClient | undefined
export function initDiscordWebhookClient() {
    if (process.env.DISCORD_WEBHOOK_URL == null) {
        Logger.warn(TAG, 'Discord webhook is not configured, important logs may not be logged to Discord.')
        return
    }

    webhook = new WebhookClient({ url: process.env.DISCORD_WEBHOOK_URL })
}

export function sendDiscordMessage(payload: string | MessagePayload | WebhookMessageCreateOptions) {
    if (webhook) {
        webhook.send(payload)
            .then(_ => {})
            .catch((e) => {
                Logger.error(TAG, "Couldn't send message to webhook channel " + JSON.stringify(payload) + "\n", e)
                Sentry.captureException(e)
            })

    }
}