package gg.beemo.latte.kafka

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
import java.util.*
import java.util.concurrent.CopyOnWriteArraySet

fun interface TopicListener {
    suspend fun onMessage(key: String, value: String, headers: KafkaRecordHeaders)
}

class KafkaConnection(
    private val kafkaHost: String,
    val clientId: String,
    private val consumerGroupId: String,
    val currentClusterId: Int,
) {

    private var producer: KafkaProducer<String, String>? = null
    private var consumer: KafkaStreams? = null
    private var shutdownHook: Thread? = null

    private val subscribedTopics = Collections.synchronizedSet(mutableSetOf<String>())

    private val topicListeners = Collections.synchronizedMap(HashMap<String, MutableSet<TopicListener>>())

    private val eventDispatcherScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val eventDispatcherErrorHandler = CoroutineExceptionHandler { _, t ->
        log.error("Uncaught error in internal Kafka record handler", t)
    }

    private val isRunning: Boolean
        // The `shutdownHook` is assigned a value as the last step of `start()`
        get() = shutdownHook != null

    suspend fun send(
        topic: String,
        key: String,
        value: String,
        headers: KafkaRecordHeaders,
        blocking: Boolean = true,
    ): String {
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
            // When requesting no blocking, just throw the request into the void and hope for the best.
            producer.send(record)
        }

        // If the record is meant for ourselves (amongst other clusters),
        // immediately dispatch it to the listeners.
        if (headers.targetClusters.isEmpty() || currentClusterId in headers.targetClusters) {
            invokeCallbacks(topic, key, value, headers)
        }
        return headers.requestId
    }

    fun on(topic: String, cb: TopicListener) {
        val listeners = topicListeners.computeIfAbsent(topic) {
            if (isRunning) {
                throw IllegalStateException("Cannot subscribe to new topic after KafkaConnection has started")
            }
            subscribedTopics.add(topic)
            CopyOnWriteArraySet()
        }
        listeners.add(cb)
    }

    fun off(topic: String, cb: TopicListener) {
        topicListeners.computeIfPresent(topic) { _, listeners ->
            listeners.remove(cb)
            if (listeners.size == 0) {
                subscribedTopics.remove(topic)
                null
            } else {
                listeners
            }
        }
    }

    fun start() {
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

    fun destroy() {
        consumer?.close()
        consumer = null
        producer?.close()
        producer = null
        shutdownHook?.let { Runtime.getRuntime().removeShutdownHook(it) }
        shutdownHook = null
        subscribedTopics.clear()
        topicListeners.clear()
        eventDispatcherScope.cancel()
    }

    private fun createTopics() {
        log.debug("Creating missing topics, target topics: $subscribedTopics")
        val props = Properties()
        props[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaHost

        val client = AdminClient.create(props)
        val existingTopics = client.listTopics().names().get()
        val missingTopics = subscribedTopics.filter { !existingTopics.contains(it) }
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
        props[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaHost
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
        if (subscribedTopics.isEmpty()) {
            log.warn("No topics have been subscribed to, not initializing Central Kafka Consumer")
            return
        }
        log.debug("Creating Consumer")

        val props = Properties()
        props[StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG] = Serdes.String().javaClass
        props[StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG] = Serdes.String().javaClass
        // Server to connect to
        props[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaHost
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
        val source = streamsBuilder.stream<String, String>(subscribedTopics)

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

    private fun handleIncomingRecord(topic: String, record: Record<String, String>) =
        eventDispatcherScope.launch(eventDispatcherErrorHandler) {
            val headers = KafkaRecordHeaders(record.headers())
            if (headers.targetClusters.isNotEmpty() && currentClusterId !in headers.targetClusters) {
                // If there is a target cluster restriction and this record wasn't meant for us,
                // discard it immediately without notifying any listeners.
                return@launch
            }
            if (headers.sourceCluster == currentClusterId) {
                // If this record was sent by ourselves, discard it too, as we already dispatch events
                // to our listeners in `send()` to avoid the round trip through Kafka.
                return@launch
            }
            invokeCallbacks(topic, record.key(), record.value(), headers)
        }

    private fun invokeCallbacks(topic: String, key: String, value: String, headers: KafkaRecordHeaders) =
        eventDispatcherScope.launch(eventDispatcherErrorHandler) {
            val listeners = topicListeners[topic]
            for (listener in listeners ?: return@launch) {
                try {
                    listener.onMessage(key, value, headers)
                } catch (t: Throwable) {
                    log.error("Uncaught error in KafkaConnection listener", t)
                }
            }
        }

}
