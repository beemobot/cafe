package gg.beemo.latte.broker.kafka

import gg.beemo.latte.broker.BaseBrokerMessageHeaders
import gg.beemo.latte.broker.BrokerConnection
import gg.beemo.latte.broker.TopicListener
import gg.beemo.latte.logging.log
import kotlinx.coroutines.*
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.errors.MissingSourceTopicException
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler
import org.apache.kafka.streams.processor.api.ProcessorSupplier
import org.apache.kafka.streams.processor.api.Record
import java.lang.IllegalArgumentException
import java.util.*


class KafkaConnection(
    kafkaHosts: Array<String>,
    override val clientId: String,
    private val consumerGroupId: String,
    override val clusterId: String,
) : BrokerConnection() {

    private val kafkaHostsString = kafkaHosts.joinToString(",")

    private var producer: KafkaProducer<String, String>? = null
    private var consumer: KafkaStreams? = null
    private var shutdownHook: Thread? = null

    private val isRunning: Boolean
        // The `shutdownHook` is assigned a value as the last step of `start()`
        get() = shutdownHook != null

    override suspend fun send(
        topic: String,
        key: String,
        value: String,
        headers: BaseBrokerMessageHeaders,
        blocking: Boolean,
    ): String {
        if (headers !is KafkaMessageHeaders) {
            throw IllegalArgumentException("KafkaConnection requires headers of type KafkaMessageHeaders to be passed, got ${headers.javaClass.name} instead")
        }

        if (maybeShortCircuitOutgoingMessage(topic, key, value, headers)) {

            val producer = this.producer ?: throw IllegalStateException("Producer is not initialized")
            val record = ProducerRecord(topic, key, value)
            headers.applyTo(record.headers())

            if (blocking) {
                withContext(Dispatchers.IO) {
                    // This can block for up to max.block.ms while gathering metadata.
                    // The request itself will be sent async, once the metadata is fetched.
                    producer.send(record).get()
                }
            } else {
                // When no blocking is requested, just throw the message into the void and hope for the best.
                producer.send(record)
            }
        }

        return headers.requestId
    }

    override fun start() {
        check(!isRunning) { "KafkaConnection is already running!" }
        log.debug("Starting Kafka Connection")
        createTopics()
        createProducer()
        createConsumer()

        shutdownHook = Thread({
            shutdownHook = null
            destroy()
        }, "Kafka Connection ($consumerGroupId) Shutdown Hook")
        Runtime.getRuntime().addShutdownHook(shutdownHook)
        log.debug("Kafka Connection is fully initialized")
    }

    override fun destroy() {
        super.destroy()
        consumer?.close()
        consumer = null
        producer?.close()
        producer = null
        shutdownHook?.let { Runtime.getRuntime().removeShutdownHook(it) }
        shutdownHook = null
    }

    override fun createHeaders(targetClusters: Set<String>?, requestId: String?): BaseBrokerMessageHeaders {
        return KafkaMessageHeaders(this.clientId, this.clusterId, targetClusters, requestId)
    }

    override fun on(topic: String, cb: TopicListener) {
        if (!topicListeners.containsKey(topic) && isRunning) {
            // NOTE: It might be possible to recreate and reconnect the stream with new topics,
            //       but at this point it's not worth the effort, given this kind of dynamic topic
            //       addition doesn't happen in practice.
            throw IllegalStateException("Cannot subscribe to new topic after KafkaConnection has started")
        }
        super.on(topic, cb)
    }

    private fun createTopics() {
        val listeningTopics = topicListeners.keys
        log.debug("Creating missing topics, target topics: $listeningTopics")
        val props = Properties()
        props[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaHostsString

        val client = AdminClient.create(props)
        val existingTopics = client.listTopics().names().get()
        val missingTopics = listeningTopics.filter { !existingTopics.contains(it) }
        log.debug("Missing topics: $missingTopics")
        if (missingTopics.isNotEmpty()) {
            client.createTopics(
                missingTopics.map { NewTopic(it, 1, 1) }
            ).all().get()
        }
        log.debug("Created all missing topics")
    }

    private fun createProducer() {
        log.debug("Creating Producer")
        val props = Properties()
        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.qualifiedName
        props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.qualifiedName
        // Server to connect to
        props[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaHostsString
        // Readable name of this client for debugging purposes
        props[ProducerConfig.CLIENT_ID_CONFIG] = consumerGroupId
        // Require all broker replicas to have acknowledged the request
        props[ProducerConfig.ACKS_CONFIG] = "all"
        // Limit time send() can block waiting for topic metadata
        props[ProducerConfig.MAX_BLOCK_MS_CONFIG] = 10_000
        // Max time for the server to respond to a request
        props[ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG] = 10_000
        // Max time for the server to report successful delivery, including retries
        props[ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG] = 60_000
        // Amount of times to retry a failed request
        props[ProducerConfig.RETRIES_CONFIG] = 10
        // Enable idempotence logic stuff
        props[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
        // Group requests sent within the same 1 ms window into a single batch
        props[ProducerConfig.LINGER_MS_CONFIG] = 1
        // Maximum Size of a single batch (in bytes)
        props[ProducerConfig.BATCH_SIZE_CONFIG] = 16 * 1024

        producer = KafkaProducer<String, String>(props)
        log.debug("Producer has been created")
    }

    private fun createConsumer() {
        if (topicListeners.isEmpty()) {
            log.warn("No topics have been subscribed to, not initializing Central Kafka Consumer")
            return
        }
        log.debug("Creating Consumer")

        val props = Properties()
        props[StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG] = Serdes.String().javaClass
        props[StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG] = Serdes.String().javaClass
        // Server to connect to
        props[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaHostsString
        // Name of this client for group management
        props[StreamsConfig.APPLICATION_ID_CONFIG] = consumerGroupId
        props[StreamsConfig.CLIENT_ID_CONFIG] = consumerGroupId
        // Max time a task may stall and retry due to errors
        props[StreamsConfig.TASK_TIMEOUT_MS_CONFIG] = 5_000
        // Max time for the server to respond to a request
        props[StreamsConfig.REQUEST_TIMEOUT_MS_CONFIG] = 10_000
        // "Note that exactly-once processing requires a cluster of at least three brokers by default"
        // - let's hope for the best
        props[StreamsConfig.PROCESSING_GUARANTEE_CONFIG] = StreamsConfig.EXACTLY_ONCE_V2
        // Commit the stream progress every 100ms
        props[StreamsConfig.COMMIT_INTERVAL_MS_CONFIG] = 100

        val streamsBuilder = StreamsBuilder()
        val source = streamsBuilder.stream<String, String>(topicListeners.keys)

        source.process(ProcessorSupplier {
            KafkaProcessorImpl { record, context ->
                val meta = context.recordMetadata()
                if (meta.isPresent) {
                    handleIncomingRecord(meta.get().topic(), record)
                }
            }
        })

        consumer = KafkaStreams(streamsBuilder.build(), props).apply {
            setUncaughtExceptionHandler { ex ->
                log.error("Error in KafkaStreams", ex)
                if (ex is MissingSourceTopicException) {
                    log.info("Got MissingSourceTopicException in Consumer, trying to re-create missing topics")
                    try {
                        synchronized(::createTopics) {
                            createTopics()
                        }
                    } catch (t: Throwable) {
                        log.error(
                            "Error in KafkaStreams: Got MissingSourceTopicException but couldn't re-create topics",
                            t
                        )
                    }
                }
                StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.REPLACE_THREAD
            }
            start()
        }
        log.debug("Consumer has been created")
    }

    private fun handleIncomingRecord(topic: String, record: Record<String, String>) {
        val headers = KafkaMessageHeaders(record.headers())
        handleIncomingMessage(topic, record.key(), record.value(), headers)
    }

}
