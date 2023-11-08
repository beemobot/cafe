import {Server} from "../../types/server.js";
import {ChargebeeCustomer, ChargebeeSubscription} from "../../types/chargebee.js";
import {sendDiscordMessage} from "../../connections/discord.js";
import {createActivatedEmbed, createCancelledEmbed} from "./embeds.js";

export function sendWebhook(server: Server, plan: string, subscription: ChargebeeSubscription, customer: ChargebeeCustomer) {
    if (plan !== 'none') {
        sendDiscordMessage({
            embeds: [
                createActivatedEmbed(server,  plan, subscription, customer)
            ]
        })
        return
    }

    sendDiscordMessage({
        embeds: [createCancelledEmbed(server, subscription, customer)]
    })
}