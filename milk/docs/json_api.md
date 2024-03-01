# Documentations for Third-Party Developers

This documentation primarily discusses the JSON API (Application Interface) available to everyone.

## Limitations of Text API

```text
logs.beemo.gg/raid/:id
```

Following some recent testings, we've discovered that raid logs reaching over 500,000 users took **tens of megabytes** 
to load, and that isn't ideal for the general users and for our systems. As such, after careful consideration, we've 
decided to limit the Text API (`/raid/:id`) to display, at maximum, 2,000 users, which is about as much as we expect 
the public to skim at maximum without using a tool to aggregate the data.

For people who aggregates the data using specialized tools, we have a JSON API (`/raid/:id.json`) that is paginated
available to use. You can find details about it in the following:
1. [`OpenAPI Schema`](openapi.yaml)
2. [`JSON API`](json_api.md)

## JSON API

```text
logs.beemo.gg/raid/:id.json
```

In this section, the specifications of the JSON API will be discussed and understood to provide clarity and understanding 
over how one can use this to collect data about their raid.

> **Warning**
> 
> Please note that this documentation is written for people who have some understanding of general 
> data types and JSON as this is intended for people who are usually developing their own in-house tools.

### Specifications

Our JSON API is different from the Text API as the data here is chunked into different pages by 200 users each page, 
and contains more detailed information of users, such as their avatar hash, when the account was created.

### Date Format

Dates are formatted under the [`ISO 8601`](https://en.wikipedia.org/wiki/ISO_8601) specification[^1], which are as follows:
```text
YYYY-MM-DDTHH:mm:ss.sssZ
```

[^1]: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/toISOString

### Response Schema
```json
{
  "raid": {
    "startedAt": "date",
    "concludedAt": "nullable(date)",
    "guild": "int64 as string",
    "size": "int32"
  },
  "users": {
    "next": "nullable(date)",
    "size": "int16",
    "data": "array of User"
  }
}
```

### User Schema
```json
{
  "id": "int64 as string",
  "name": "string",
  "joinedAt": "date",
  "createdAt": "date",
  "avatarHash": "nullable(string)"
}
```

### Query Parameters
- `cursor`: an ISO-8601 date that indicates what next set of data to get.
  - **description**: an ISO-8601 date that indicates what next set of data to get.
  - **type**: ISO-8601 date or `null`.
  - **example**: `?cursor=2023-11-02T01:02:36.978Z`

### Querying data

To query the initial data, one needs to send a request to `logs.beemo.gg/raid/:id.json` where `:id` refers to the 
raid identifier provided by Beemo, it should look gibberish and random, such as: `Ht2Erx76nj13`. In this example, we'll 
use `Ht2Erx76nj13` as our `:id`. 

> **Note**
> 
> `Ht2Erx76nj13` is a sample raid from our tests. It may or may not exist on the actual `logs.beemo.gg` domain as 
> of writing, but it could exist at some point. We recommend using your own raid identifier to follow along with 
> our example.

Depending on your tooling, this may be different, but in this example, we'll be using `curl` in our command-line 
to demonstrate.
```shell
curl http://logs.beemo.gg/raid/Ht2Erx76nj13.json
```

Running the above command gives us a very long JSON response, which follows our [schema](#response-schema) containing 
information about the raid and the users.
```json
{
  "raid": {
    "startedAt": "2023-11-02T01:25:36.970Z",
    "concludedAt": null,
    "guild": "697474023914733575",
    "size": 5000
  },
  "users": {
    "next": "2023-11-02T01:02:36.978Z",
    "size": 200,
    "data": [
      {
        "id": "972441010812461952",
        "name": "9hsr6JA5jk7vVA35",
        "joinedAt": "2023-11-02T01:01:36.975Z",
        "createdAt": "2023-11-14T01:25:36.975Z",
        "avatarHash": null
      },
      {
        "id": "495325603979310784",
        "name": "TW871dd7YTb7sZv9",
        "joinedAt": "2023-11-02T01:01:36.975Z",
        "createdAt": "2023-11-03T01:25:36.975Z",
        "avatarHash": null
      },
      ...,
      {
        "id": "732275137613676288",
        "name": "EbWp0ORBYH33BOX5",
        "joinedAt": "2023-11-02T01:02:36.978Z",
        "createdAt": "2023-11-21T01:25:36.978Z",
        "avatarHash": null
      }
    ]
  }
}
```

From the above output, we can see that the `next` property is filled, which means, we can use that to possibly query 
the next set of data, if there is any. This `next` property also happens to be the same as the `joinedAt` time of the 
**last user in the array**, in this case, the user with the id of "732275137613676288" and the name of "EbWp0ORBYH33BOX5".

To query the next set of information, all we need to do is add `?cursor=<next property's value>` to the link. In our 
example, our `<next property's value>` is `2023-11-02T01:02:36.978Z`, which means, we have to add `?cursor=2023-11-02T01:02:36.978Z` to our link.

```shell
curl http://logs.beemo.gg/raid/Ht2Erx76nj13.json?cursor=2023-11-02T01:02:36.978Z
```

Running that command on our command-line gives us a similar output  to the above, but instead, we get a different set 
of users and a different `next` property value that directly links with the **last user in the array**'s `joinedAt`.

```json
{
  "raid": {
    "startedAt": "2023-11-02T01:25:36.970Z",
    "concludedAt": null,
    "guild": "697474023914733575",
    "size": 5000
  },
  "users": {
    "next": "2023-11-02T01:03:36.982Z",
    "size": 200,
    "data": [
      {
        "id": "819796273096448256",
        "name": "676kq1AUwXh8Ygwf",
        "joinedAt": "2023-11-02T01:02:36.979Z",
        "createdAt": "2023-11-09T01:25:36.979Z",
        "avatarHash": null
      },
      {
        "id": "679522196993485440",
        "name": "93j0HK2MvEud9n1Y",
        "joinedAt": "2023-11-02T01:02:36.979Z",
        "createdAt": "2023-11-26T01:25:36.979Z",
        "avatarHash": null
      },
      ...,
      {
        "id": "151281220654798656",
        "name": "W04Qy8a4p1bZxSMu",
        "joinedAt": "2023-11-02T01:03:36.982Z",
        "createdAt": "2023-11-25T01:25:36.982Z",
        "avatarHash": null
      }
    ]
  }
}
```

And similar to the previous one, you can then request the next set of data by replacing the value of `cursor` on your 
link to the value of `next` and keep repeating until you get all the data that you want.

## Have more questions?

If you have more questions, feel free to come over at our [Discord Server](https://beemo.gg/discord) and ask over there, 
we'll happily answer to our best capacity!