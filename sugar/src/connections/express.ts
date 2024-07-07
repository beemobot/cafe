import express from "express";
import expressBasicAuth from "express-basic-auth";
import GetWebhookRoute from "../routes/PostWebhookRoute.js";
import * as Sentry from '@sentry/node';
import {Logger} from "@beemobot/common";
// ^ This needs to be updated; Probably @beemobot/cafe

import {TAG} from "../constants/logging.js";
export const server = express()

export function initExpressServer() {
    const username = process.env.BASIC_AUTH_USERNAME
    const password = process.env.BASIC_AUTH_PASSWORD

    if (username == null || password == null) {
        Logger.error(TAG, 'Basic authentication is not configured. It must be configured for authentication purposes.')
        process.exit()
    }

    let account: any = {}
    account[username] = password

    if (process.env.SERVER_PORT == null) {
        Logger.error(TAG, 'SERVER_PORT is a required configuration and must be configured to start the HTTP server.')
        process.exit()
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
        Logger.info(TAG, 'Sugar is now ready to receive connections. ' + JSON.stringify({
            connection: 'http://localhost:' + process.env.SERVER_PORT,
            webhook: 'http://localhost:' + process.env.SERVER_PORT + "/webhook"
        }))
    })
}