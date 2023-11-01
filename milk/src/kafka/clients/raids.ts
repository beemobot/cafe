import {BrokerClient, BrokerMessage, KafkaConnection, Logger} from "@beemobot/common";
// ^ This needs to be updated; Probably @beemobot/cafe
import {run} from "../../utils/retry.js";
import {RaidManagementData} from "../../types/raid.js";
import {logIssue} from "../../connections/sentry.js";
import {TAG} from "../../constants/logging.js";
import {
    RAID_MANAGEMENT_BATCH_INSERT_KEY,
    RAID_MANAGEMENT_CLIENT_TOPIC,
    RAID_MANAGEMENT_CONCLUDE_RAID
} from "../../constants/raid_management_kafka.js";
import {concludeRaid, createRaid, getRaidByInternalId} from "../../database/raid.js";
import {RaidUser} from "@prisma/client";
import {insertRaidUsers} from "../../database/raid_users.js";

export class RaidManagementClient extends BrokerClient<RaidManagementData> {
    constructor(conn: KafkaConnection) {
        super(conn, RAID_MANAGEMENT_CLIENT_TOPIC);
        this.on(RAID_MANAGEMENT_BATCH_INSERT_KEY, this.onBatchInsertRaidUsers)
        this.on(RAID_MANAGEMENT_CONCLUDE_RAID, this.onConcludeRaid)
    }

    private async onConcludeRaid(message: BrokerMessage<RaidManagementData>) {
        if (message.value == null || message.value.request == null) {
            Logger.warn(TAG, `Received a message on ${RAID_MANAGEMENT_CONCLUDE_RAID} but no request details was found.`)
            return
        }

        let {raidId, concludedAt, guildIdString} = message.value.request
        if (concludedAt == null) {
            // Assume that the raid was just concluded, theoretically, the raid just concluded the moment we receive
            // this message as there is no other reason to send a message to this key if not for concluding a raid.
            concludedAt = new Date()
        }

        let raid = await getRaidByInternalId(raidId)
        if (raid == null) {
            logIssue(`Received a request to conclude a raid, but the raid is not in the database. [raid=${raidId}]`)
            return
        }

        if (raid.concluded_at != null) {
            Logger.warn(TAG, `Received a request to conclude a raid, but the raid is already concluded. [raid=${raidId}]`)
            return
        }

        Logger.info(TAG, `Concluding raid ${raidId} from guild ${guildIdString}.`)
        raid = await run(
            'conclude_raid',
            async () => concludeRaid(raid!.external_id, raid!.internal_id, concludedAt),
            0.2,
            25
        )

        await message.respond({ response: { externalId: raid!.external_id }, request: null })
    }
    
    private async onBatchInsertRaidUsers(message: BrokerMessage<RaidManagementData>) {
        if (message.value == null || message.value.request == null) {
            Logger.warn(TAG, `Received a message on ${RAID_MANAGEMENT_BATCH_INSERT_KEY} but no request details was found.`)
            return
        }

        const request = message.value.request

        if (request.users.length > 0) {
            Logger.info(TAG, `Inserting ${request.users.length} users to the raid ${request.raidId}.`)
            const users = request.users.map((user) =>  {
                return {
                    internal_raid_id: request.raidId,
                    user_id: BigInt(user.idString),
                    name: user.name,
                    avatar_hash: user.avatarHash,
                    created_at: user.createdAt,
                    joined_at: user.joinedAt
                } satisfies RaidUser
            })

            await run(
                'insert_raid_users',
                async () => insertRaidUsers(users),
                2,
                25
            )
        }

        let raid = await getRaidByInternalId(request.raidId)
        if (raid == null) {
            Logger.info(TAG, `Creating raid ${request.raidId} from guild ${request.guildIdString}.`)
            raid = await run(
                'create_raid',
                async () => createRaid(request.raidId, request.guildIdString, request.concludedAt),
                1,
                25
            )
        }

        await message.respond({ response: { externalId: raid!.external_id }, request: null })
    }
}