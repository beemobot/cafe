package gg.beemo.vanilla

import gg.beemo.latte.CommonConfig
import gg.beemo.latte.broker.kafka.KafkaConnection
import gg.beemo.latte.config.Configurator
import gg.beemo.latte.logging.Log
import kotlinx.coroutines.runBlocking

object Vanilla {

    private val log by Log

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        log.info("Starting vanilla")
        log.debug("Loading configuration")
        Configurator.create().mirror(Config::class.java)

        log.debug("Initializing Kafka connection")
        val brokerConnection = KafkaConnection(
            Config.KAFKA_HOST,
            CommonConfig.BrokerServices.VANILLA,
            "0", // There will only ever be one instance of vanilla
            Config.KAFKA_USE_TLS,
        )

        log.debug("Initializing Kafka Ratelimit client")
        RatelimitClient(brokerConnection)

        log.debug("Starting Kafka connection")
        brokerConnection.start()

        log.info("Initialization done! Listening for ratelimit requests.")
    }

}
