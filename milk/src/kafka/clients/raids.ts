import {BrokerClient, BrokerMessage, KafkaConnection, Logger} from "@beemobot/common";
// ^ This needs to be updated; Probably @beemobot/cafe
import {run} from "../../utils/retry.js";
import {RaidManagementData} from "../../types/raid.js";
import {logIssue} from "../../connections/sentry.js";
import {TAG} from "../../constants/logging.js";
import {
    RAID_MANAGEMENT_BATCH_INSERT_KEY,
    RAID_MANAGEMENT_CLIENT_TOPIC,
    RAID_MANAGEMENT_CONCLUDE_RAID, RAID_MANAGEMENT_CREATE_RAID
} from "../../constants/raid_management_kafka.js";
import {concludeRaid, createRaid, getRaidByInternalId} from "../../database/raid.js";
import {RaidUser} from "@prisma/client";
import {insertRaidUsers} from "../../database/raid_users.js";

export class RaidManagementClient extends BrokerClient<RaidManagementData> {
    constructor(conn: KafkaConnection) {
        super(conn, RAID_MANAGEMENT_CLIENT_TOPIC);
        this.on(RAID_MANAGEMENT_CREATE_RAID, this.onCreateRaid)
        this.on(RAID_MANAGEMENT_BATCH_INSERT_KEY, this.onBatchInsertRaidUsers)
        this.on(RAID_MANAGEMENT_CONCLUDE_RAID, this.onConcludeRaid)
    }

    private async onCreateRaid(message: BrokerMessage<RaidManagementData>) {
        if (message.value == null || message.value.request == null) {
            Logger.warn(TAG, `Received a message on ${RAID_MANAGEMENT_CREATE_RAID} but no request details was found.`)
            return
        }

        const request = message.value.request

        let raid = await getRaidByInternalId(request.raidId)
        if (raid == null) {
            Logger.info(TAG, `Creating raid ${request.raidId} from guild ${request.guildId}.`)
            raid = await run(
                'create_raid',
                async () => createRaid(request.raidId, request.guildId, null),
                1,
                12
            )
        }

        await message.respond({ response: { publicId: raid?.public_id, acknowledged: true }, request: null })
    }

    private async onConcludeRaid(message: BrokerMessage<RaidManagementData>) {
        if (message.value == null || message.value.request == null) {
            Logger.warn(TAG, `Received a message on ${RAID_MANAGEMENT_CONCLUDE_RAID} but no request details was found.`)
            return
        }

        let {raidId, concludedAtMillis, guildId} = message.value.request
        let conclusionDate: Date = new Date(concludedAtMillis ?? Date.now())

        let raid = await getRaidByInternalId(raidId)
        if (raid == null) {
            logIssue(`Received a request to conclude a raid, but the raid is not in the database. [raid=${raidId}]`)
            return
        }

        if (raid.concluded_at != null) {
            Logger.warn(TAG, `Received a request to conclude a raid, but the raid is already concluded. [raid=${raidId}]`)
            return
        }

        Logger.info(TAG, `Concluding raid ${raidId} from guild ${guildId}.`)
        raid = await run(
            'conclude_raid',
            async () => concludeRaid(raid!.public_id, raid!.id, conclusionDate),
            0.2,
            12
        )

        await message.respond({ response: { publicId: raid!.public_id, acknowledged: true }, request: null })
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
                    raid_id: request.raidId,
                    id: BigInt(user.id),
                    name: user.name,
                    avatar_hash: user.avatarHash,
                    created_at: new Date(user.createdAtMillis),
                    joined_at: new Date(user.joinedAtMillis)
                } satisfies RaidUser
            })

            await run(
                'insert_raid_users',
                async () => insertRaidUsers(users),
                2,
                12
            )
        }

        await message.respond({ response: { publicId: null, acknowledged: true }, request: null })
    }
}