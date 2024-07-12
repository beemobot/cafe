# Developer Documentations for Kafka client

This documentation is primarily for developers of Beemo. The Kafka  client isn't something 
accessible to third-parties, therefore, more than likely, this is of no-use for third-parties.

### Key Points
- [`Client Specifications`](#client-specifications) talks about the different parts of the client.
  - [`overview`](#overview) summarizes some key points  of the client.
  - [`keys`](#keys)
    - [`create-raid`](#create-raid) used to create a raid.
    - [`batch-insert-raid-users`](#batch-insert-raid-users) used to insert one or more bots detected; creates the Raid if it doesn't exist.
    - [`conclude-raid`](#conclude-raid) used to conclude an existing raid; if no date provided, uses current time.
  - [`schemas`](#schemas)
    - [`RaidManagementData`](#raid-management-data) is the primary type transported between clients.
    - [`RaidManagementRequest`](#raid-management-request) is used by a requesting client to add more raid users, 
    start a raid or conclude a raid. Primarily created clients such as Tea.
    - [`RaidManagementResponse`](#raid-management-response) is used by a responding client after processing a request. 
    This  is primarily used by clients such as Milk.
    - [`RaidManagementUser`](#raid-management-user) is used to hold information about a bot detected.

## Client Specifications

In this section, the different specifications of the Kafka client will be discussed and understood to 
provide some understanding over how the Kafka client of Milk processes requests.

### Overview
- Topic: `raid-management`
- Keys:
  - `batch-insert-raid-users`
- Transport Type: 
  - [`RaidManagementData`](#raid-management-data)

## Keys

### Batch Insert Raid Users

```yaml
key: batch-insert-raid-users
```

This is a specific key, or endpoint, in the Kafka client where clients can insert 
bots detected, start a raid or conclude a raid. It is expected that this creates a new raid 
when the `raidId` provided does not exist already. In addition, if the raid hasn't been concluded 
but a `concludedAt` property is provided then it will conclude the raid, if the raid has been 
concluded before, but a newer date has been provided then it will conclude the raid.

This endpoint expects to receive a [`RaidManagementData`](#raid-management-data) with the `request` property 
following the [`RaidManagementRequest`](#raid-management-request) schema.

After processing the request, this endpoint should respond with a similar [`RaidManagementData`](#raid-management-data) but 
with the `response` property following the [`RaidManagementResponse`](#raid-management-response) schema.

### Conclude Raid

```yaml
key: conclude-raid
```

This is a specific key, or endpoint, in the Kafka client where clients can declare an existing raid as concluded. 
It is not needed for the `concludedAt` property to be provided as it will use the current time if not provided.
Although you cannot modify the `concludedAt` of an existing raid, if a raid is already concluded then it will skip.

This endpoint expects to receive a [`RaidManagementData`](#raid-management-data) with the `request` property
following the [`RaidManagementRequest`](#raid-management-request) schema. Unlike [`batch-insert-raid-users`](#batch-insert-raid-users),  
this doesn't expect the `users` property to not be empty, even `concludedAt` can be of a zero value or even 
null as long as the `raidId` and `guildIdString` are not null.

After processing the request, this endpoint should respond with a similar [`RaidManagementData`](#raid-management-data) but
with the `response` property following the [`RaidManagementResponse`](#raid-management-response) schema.

### Create Raid

```yaml
key: create-raid
```

This is a specific key, or endpoint, in the Kafka client where clients can create a new raid in the database. This should be  
done at the start before the users are added, and should be awaited otherwise it will lead to a foreign key issue.

This endpoint expects to receive a [`RaidManagementData`](#raid-management-data) with the `request` property
following the [`RaidManagementRequest`](#raid-management-request) schema. Unlike [`batch-insert-raid-users`](#batch-insert-raid-users),  
this doesn't expect the `users` property to not be empty as long as the `raidId` and `guildIdString` are not null.

After processing the request, this endpoint should respond with a similar [`RaidManagementData`](#raid-management-data) but
with the `response` property following the [`RaidManagementResponse`](#raid-management-response) schema.

## Schemas

### Raid Management Data

```json
{
  "request": "nullable(RaidManagementRequest)",
  "response": "nullable(RaidManagementResponse)"
}
```

Used by the clients to transport either a request or a response without the need to perform additional identification.

- `request` is a nullable property containing the request details, used by the requesting client. This should be 
guaranteed from a request, otherwise there is a bug with that client.
- `response` is a nullable property containing the response details, used by the responding client. This should be
guaranteed from a response of a client, otherwise there is a bug with that client.


### Raid Management Request
```json
{
  "raidId": "string",
  "guildIdString": "string",
  "users": "array(RaidManagementUser)",
  "concludedAt": "nullable(date as string)"
}
```

Used by a requesting client to start a raid, insert bots detected or conclude a raid.

- `raidId` refers to the internal raid id of the raid. Clients shouldn't use the external raid id as that is created 
and used only by Milk itself.
- `guildIdString` refers to the id of the guild that this raid belonged to. It must be of `string` type due to 
the nature of JavaScript not inherently supporting `int64` or `long` type.
- `users` refers to the bots detected in the raid, this can be an empty array when simply concluding a raid.
- `concludedAt` refers to the date when the raid should be declared as concluded.

### Raid Management Response
```json
{
  "publicId": "nullable(string)",
  "acknowledged": true
}
```

Used by a responding client to notify that the request was processed, and a publicly accessible id is now 
available to be shared in the log channels.

- `externalId` refers to the publicly accessible id that can be used in `/raid/:id`

### Raid Management User
```json
{
  "idString": "string",
  "name": "string",
  "avatarHash": "nullable(string)",
  "createdAt": "date as string",
  "joinedAt": "date as string"
}
```

Contains information about a bot that was detected in a raid.

- `idString` refers to the id of the bot's account.
- `name` refers to the name of the bot during detection.
- `avatarHash` refers to the hash of the bot's avatar during detection.
- `createdAt` refers to the creation time of the bot.
- `joinedAt` refers to when the bot joined the server.