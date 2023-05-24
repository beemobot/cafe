### Cafe

The Beemo Cafe is a monorepo containing all the public microservices that are powering Beemo. The repo consists of the following services:

|                              Name                             |     Purpose     |                                     Description                                     |
|:-------------------------------------------------------------:|:---------------:|:-----------------------------------------------------------------------------------:|
|   [Latte](https://github.com/beemobot/cafe/tree/main/latte)   |     Commons     |  A set of Java-Kotlin commons that are used all over Beemo's Java-Kotlin services.  |
|    [Milk](https://github.com/beemobot/cafe/tree/main/milk)    |   Public Logs   |            Handles storing and public viewing of raid logs.                         |
|   [Sugar](https://github.com/beemobot/cafe/tree/main/sugar)   |  Subscriptions  |                     Processes Chargebee subscriptions for Beemo.                    |
| [Vanilla](https://github.com/beemobot/cafe/tree/main/vanilla) | Central Manager | The central manager for Tea clusters to handle cases such as global ratelimit, etc. |
|   [Water](https://github.com/beemobot/cafe/tree/main/water)   |     Commons     |   A set of Typescript commons that are used all over Beemo's Typescript services.   |
