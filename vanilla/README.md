# Vanilla

Vanilla is the centralized manager for tea clusters.
While currently only serving global ratelimit requests, in the future it will be responsible for
managing all shards and clusters.

All communication is done through Kafka.
For this to work, you have to set the `KAFKA_HOST` environment variable to the address(es) of your Kafka cluster.
