import {ChargebeeCustomer, ChargebeeEvent, ChargebeeSubscription} from "../types/chargebee.js";
import { ValidationError } from "runtypes";
import express from "express";
import { Server } from "../types/server.js";
import * as Sentry from '@sentry/node';
import {createTaskName, retriable} from "../utils/utils.js";
import {determinePlan, updatePlan} from "../utils/plans.js";

const router = express.Router()

// IMPORTANT: Make sure all these events are implemented before adding them to this list.
const supported_events: string[] = [
    "subscription_created",
    "subscription_created_with_backdating",
    "subscription_started",
    "subscription_reactivated",
    "subscription_reactivated_with_backdating",
    "subscription_paused",
    "subscription_cancelled",
    "subscription_resumed"
]

// IMPORTANT: This contains an array of all the ids that we have processed, to prevent duplicate handling within
// the lifetime of the application.
const processed_events: Set<string> = new Set();

router.post('/webhook/', async (request, response) => {
    try {
        if (request.body == null) {
            response.status(400).json({ error: 'Invalid request.' })
            return
        }

        const event = ChargebeeEvent.check(request.body)

        if (!supported_events.includes(event.event_type.toLowerCase())) {
            response.status(400).json({ error: 'Unsupported event.'})
            return
        }

        if (processed_events.has(event.id)) {
            response.status(204).json({ error: 'Duplicate event.'})
            return
        }

        processed_events.add(event.id)
        const subscription: ChargebeeSubscription = event.content.subscription
        const customer: ChargebeeCustomer = event.content.customer

        // DEBATABLE: We should just tell Chargebee to go ahead and continue its day before we finish processing.
        // Because the retries on our side (e.g. when persisting cancellations to db or sending to kafka) takes more
        // than 10 seconds each retry which is way past Chargebee's timeout.
        response.sendStatus(204)

        const server: Server = { id: subscription.cf_discord_server_id }
        retriable(createTaskName(subscription, customer),
            async () => await updatePlan(server, determinePlan(subscription), subscription, customer))
    } catch (exception: any) {
        if (exception instanceof ValidationError) {
            response.status(400).json({ error: 'Invalid Request.' })
            return
        }

        // IMPORTANT: Make-shift way to transform an object error into an Error.
        if (exception.message != null && exception.constructor.name === 'Object') {
            Sentry.captureException(new Error(exception.message))
        } else {
            Sentry.captureException(exception)
        }

        response.status(500)
        console.error('An exception occurred while trying to handle webhook request.', exception)
    }
})

export default router