export type RaidManagementData = {
    request: RaidManagementRequest | null,
    response: RaidManagementResponse | null
}

export type RaidManagementRequest = {
    raidId: string,
    guildId: string,
    users: RaidManagementUser[],
    concludedAtMillis: number | null
}

export type RaidManagementResponse = {
    publicId: string
}

export type RaidManagementUser = {
    id: string,
    name: string,
    avatarHash: string | null,
    createdAtMillis: number,
    joinedAtMillis: number
}

export type PublicRaidUser = {
    id: string,
    name: string,
    avatarHash: string | null,
    createdAt: Date,
    joinedAt: Date
}