import {Logger} from "@beemobot/water";
import {TAG} from "../index.js";
import {MessagePayload, WebhookClient, WebhookMessageCreateOptions} from "discord.js";
import * as Sentry from "@sentry/node"

export let webhook: WebhookClient | undefined
function init() {
    if (process.env.DISCORD_WEBHOOK_URL == null) {
        Logger.warn(TAG, 'Discord webhook is not configured, important logs may not be logged to Discord.')
        return
    }

    webhook = new WebhookClient({ url: process.env.DISCORD_WEBHOOK_URL })
}

function send(payload: string | MessagePayload | WebhookMessageCreateOptions) {
    if (webhook) {
        webhook.send(payload)
            .then(_ => {})
            .catch((e) => {
                Logger.error(TAG, "Couldn't send message to webhook channel " + JSON.stringify(payload) + "\n", e)
                Sentry.captureException(e)
            })

    }
}

export const DiscordWebhook = { init: init, send: send }