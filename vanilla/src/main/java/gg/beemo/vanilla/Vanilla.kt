package gg.beemo.vanilla

import gg.beemo.latte.CommonConfig
import gg.beemo.latte.broker.rabbitmq.RabbitConnection
import gg.beemo.latte.config.Configurator
import gg.beemo.latte.logging.Log
import io.grpc.Server
import io.grpc.ServerBuilder
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.LogManager

object Vanilla {

    private val log by Log

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        log.info("Starting vanilla")
        log.debug("Loading configuration")
        Configurator.create().mirror(Config::class.java)

        log.debug("Initializing Kafka connection")
        val brokerConnection = RabbitConnection(
            rabbitHosts = Config.RABBIT_HOST,
            serviceName = CommonConfig.BrokerServices.VANILLA,
            instanceId = "0", // There will only ever be one instance of vanilla
            useTls = Config.RABBIT_USE_TLS,
            username = Config.RABBIT_USERNAME,
            password = Config.RABBIT_PASSWORD,
        )

        log.debug("Initializing Broker Ratelimit client")
        val ratelimitClient = BrokerRpcRatelimitClient(brokerConnection)

        log.debug("Initializing gRPC Ratelimit client")
        val grpcServer: Server = ServerBuilder.forPort(Config.GRPC_PORT)
            .addService(GrpcRatelimitService())
            .build()
            .start()

        Runtime.getRuntime().addShutdownHook(Thread({
            log.info("Destroying everything")
            ratelimitClient.destroy()
            brokerConnection.destroy()
            grpcServer.shutdown().awaitTermination()
            LogManager.shutdown(true, true)
        }, "Vanilla Shutdown Hook"))

        log.debug("Starting Kafka connection")
        brokerConnection.start()

        log.info("Initialization done! Listening for ratelimit requests.")
    }

}
