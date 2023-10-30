export type RaidManagementData = {
    request: RaidManagementRequest | null,
    response: RaidManagementResponse | null
}

export type RaidManagementRequest = {
    raidId: string,
    guildIdString: string,
    users: RaidManagementUser[],
    concludedAt: (Date | string) | null
}

export type RaidManagementResponse = {
    externalId: string
}

export type RaidManagementUser = {
    idString: string,
    name: string,
    avatarHash: string | null,
    createdAt: Date | string,
    joinedAt: Date | string
}

export type PublicRaidUser = {
    id: string,
    name: string,
    avatarHash: string | null,
    createdAt: Date,
    joinedAt: Date
}