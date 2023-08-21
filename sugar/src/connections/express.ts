import express from "express";
import expressBasicAuth from "express-basic-auth";
import GetWebhookRoute from "../routes/PostWebhookRoute.js";
import * as Sentry from '@sentry/node';
import {Logger} from "@beemobot/water";
import {TAG} from "../index.js";
export const server = express()

function init() {
    const username = process.env.BASIC_AUTH_USERNAME
    const password = process.env.BASIC_AUTH_PASSWORD

    if (username == null || password == null) {
        Logger.error(TAG, 'Basic authentication is not configured, discarding request to start.')
        process.exit()
        return
    }

    let account: any = {}
    account[username] = password

    if (process.env.SERVER_PORT == null) {
        Logger.error(TAG, 'Server is not configured, discarding request to start.')
        process.exit()
        return
    }

    server
        .use(Sentry.Handlers.requestHandler())
        .get('/', (request, response) => response.json({ hello: "Do you want some sugar?" }))
        .use(express.json())
        .use((request, _, next) => {
            Logger.info(TAG, 'Received a request ' + JSON.stringify({ method: request.method, route: request.path, ip: request.ip, data: request.body }));
            next();
        })
        .use(expressBasicAuth({ users: account }))
        .use(GetWebhookRoute)

    server.listen(process.env.SERVER_PORT, () => {
        Logger.info(TAG, 'Sugar Sugar is now ready to receive connections. ' + JSON.stringify({
            connection: 'http://localhost:' + process.env.SERVER_PORT,
            webhook: 'http://localhost:' + process.env.SERVER_PORT + "/webhook"
        }))
    })
}

export const Expresso = { init: init }