package gg.beemo.latte.broker.kafka

import gg.beemo.latte.broker.BrokerMessageHeaders
import gg.beemo.latte.broker.BrokerConnection
import gg.beemo.latte.broker.MessageId
import gg.beemo.latte.logging.Log
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.errors.MissingSourceTopicException
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler
import org.apache.kafka.streams.processor.api.ProcessorSupplier
import org.apache.kafka.streams.processor.api.Record
import java.util.*


class KafkaConnection(
    kafkaHosts: Array<String>,
    override val serviceName: String,
    override val instanceId: String,
    private val useTls: Boolean = false,
    private val partitionCount: Int = 1,
    private val replicationFactor: Short = 1,
) : BrokerConnection() {

    override val supportsTopicHotSwap = false
    override val deferInitialTopicCreation = false
    private val kafkaHostsString = kafkaHosts.joinToString(",")
    private val log by Log

    private var producer: KafkaProducer<String, String>? = null
    private var consumer: KafkaStreams? = null

    private val isRunning: Boolean
        get() = producer != null && consumer != null

    override suspend fun abstractSend(
        topic: String,
        key: String,
        value: String,
        headers: BrokerMessageHeaders,
    ): MessageId {
        if (shouldDispatchExternallyAfterShortCircuit(topic, key, value, headers)) {

            val producer = this.producer
            checkNotNull(producer) { "Producer is not initialized" }
            val record = ProducerRecord(topic, key, value)
            record.headers().apply {
                headers.headers.forEach { (key, value) ->
                    add(key, value.toByteArray())
                }
            }

            // Asynchronously enqueue message
            producer.send(record) { metadata: RecordMetadata, ex: Exception? ->
                if (ex != null) {
                    log.error("Error enqueueing Kafka message", ex)
                } else {
                    log.trace(
                        "Enqueued message {} with key {} into topic {} at offset {}",
                        headers.messageId,
                        key,
                        metadata.topic(),
                        metadata.offset(),
                    )
                }
            }
        }

        return headers.messageId
    }

    override suspend fun abstractStart() {
        check(!isRunning) { "KafkaConnection is already running!" }
        log.debug("Starting Kafka Connection")
        createTopics()
        createProducer()
        createConsumer()
        log.debug("Kafka Connection is fully initialized")
    }

    override fun destroy() {
        log.debug("Destroying KafkaConnection")
        consumer?.close()
        consumer = null
        producer?.close()
        producer = null
        super.destroy()
    }

    override fun createTopic(topic: String) {
        checkRunningTopicsModification(topic)
    }

    override fun removeTopic(topic: String) {
        checkRunningTopicsModification(topic)
    }

    private fun checkRunningTopicsModification(topic: String) {
        if (!topicListeners.containsKey(topic) && isRunning) {
            // NOTE: It might be possible to recreate and reconnect the stream with new topics,
            //       but at this point it's not worth the effort, given this kind of dynamic topic
            //       addition doesn't happen in practice.
            throw IllegalStateException("Cannot subscribe to new topic after KafkaConnection has started")
        }
    }

    private suspend fun createTopics() {
        val listeningTopics = topicListeners.keys
        log.debug("Creating missing topics, target topics: {}", listeningTopics)
        val props = createConnectionProperties()
        val client = AdminClient.create(props)

        val existingTopics = client.listTopics().names().get()
        val missingTopics = listeningTopics.filter { !existingTopics.contains(it) }
        log.debug("Missing topics: {}", missingTopics)
        if (missingTopics.isNotEmpty()) {
            client.createTopics(
                missingTopics.map { NewTopic(it, partitionCount, replicationFactor) }
            ).all().toCompletionStage().await()
        }
        log.debug("Created all missing topics")
    }

    private fun createProducer() {
        log.debug("Creating Producer")
        val props = createConnectionProperties()
        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.qualifiedName
        props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.qualifiedName
        // Require all broker replicas to have acknowledged the request
        props[ProducerConfig.ACKS_CONFIG] = "all"
        // Limit time send() can block waiting for topic metadata
        props[ProducerConfig.MAX_BLOCK_MS_CONFIG] = 10_000
        // Max time for the server to report successful delivery, including retries
        props[ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG] = 60_000
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

        val props = createConnectionProperties()
        props[StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG] = Serdes.String().javaClass
        props[StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG] = Serdes.String().javaClass
        // Name of this client for group management
        props[StreamsConfig.APPLICATION_ID_CONFIG] = instanceId
        // Max time a task may stall and retry due to errors
        props[StreamsConfig.TASK_TIMEOUT_MS_CONFIG] = 5_000
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
                        runBlocking {
                            createTopics()
                        }
                    } catch (t: Exception) {
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
        val headersMap = record.headers().associate { it.key() to String(it.value()) }
        val headers = BrokerMessageHeaders(headersMap)
        dispatchIncomingMessage(topic, record.key(), record.value(), headers)
    }

    private fun createConnectionProperties(): Properties = Properties().apply {
        // Server(s) to connect to
        this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = kafkaHostsString
        // Whether the servers use TLS or plaintext connections
        this[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] =
            if (useTls) SecurityProtocol.SSL.name else SecurityProtocol.PLAINTEXT.name
        // Readable name of this client for debugging purposes
        this[CommonClientConfigs.CLIENT_ID_CONFIG] = instanceId
        // Max time for the server to respond to a request
        this[CommonClientConfigs.REQUEST_TIMEOUT_MS_CONFIG] = 10_000
        // Amount of times to retry a failed request
        this[CommonClientConfigs.RETRIES_CONFIG] = 10
    }

}
