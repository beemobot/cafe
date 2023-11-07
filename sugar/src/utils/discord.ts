import {Server} from "../types/server.js";
import {ChargebeeCustomer, ChargebeeSubscription} from "../types/chargebee.js";
import {Colors} from "discord.js";
import {sendDiscordMessage} from "../connections/discord.js";

export function sendWebhook(server: Server, plan: string, subscription: ChargebeeSubscription, customer: ChargebeeCustomer) {
    if (plan !== 'none') {
        sendDiscordMessage({
            embeds: [
                {
                    title: 'Subscription Activated',
                    description: 'The subscription for the following server has been successfully activated with the following details.',
                    fields: [
                        {
                            name: 'Details',
                            value: [
                                '**Server**: ' + formatServer(server),
                                '**Plan**: ' + plan.toUpperCase(),
                                '**Subscription**: ' + formatSubscription(subscription),
                                '**User**: ' + formatCustomer(customer),
                            ].join('\n'),
                        }
                    ],
                    color: Colors.Gold
                }
            ]
        })
        return
    }

    sendDiscordMessage({
        embeds: [
            {
                title: 'Subscription Cancelled',
                description: 'The subscription for the following server has been cancelled with the following details.',
                fields: [
                    {
                        name: 'Details',
                        value: [
                            '**Server**: ' + formatServer(server),
                            '**Subscription**: ' + formatSubscription(subscription),
                            '**User**: ' + formatCustomer(customer),
                        ].join('\n'),
                    }
                ],
                color: Colors.Red
            }
        ]
    })
}

function formatSubscription(subscription: ChargebeeSubscription) {
    return '[' + subscription.id + ']' +
        '(https://' + process.env.CHARGEBEE_SITE + '.chargebee.com/subscriptions/' +  subscription.id + '/details)'
}

function formatCustomer(customer: ChargebeeCustomer) {
    return customer.cf_discord_discriminator + ' (`' + customer.cf_discord_id_dont_know_disgdfindmyid + '`)'
}

function formatServer(server: Server) {
    return '`' + server.id + '`'
}