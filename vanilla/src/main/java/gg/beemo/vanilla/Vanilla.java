package gg.beemo.vanilla;

import gg.beemo.latte.CommonConfig;
import gg.beemo.latte.broker.BrokerConnection;
import gg.beemo.latte.broker.kafka.KafkaConnection;
import gg.beemo.latte.config.Configurator;
import gg.beemo.latte.logging.LoggerKt;
import org.apache.logging.log4j.Logger;

public class Vanilla {

    private static final Logger LOGGER = LoggerKt.getLogger(Vanilla.class);

    public static void main(String[] args) {
        LOGGER.debug("Loading configuration");
        Configurator.create().mirror(Config.class);

        try {
            LOGGER.debug("Initializing Kafka connection");
            BrokerConnection kafkaConnection = new KafkaConnection(
                    Config.KAFKA_HOST,
                    "vanilla",
                    "vanilla",
                    CommonConfig.VANILLA_CLUSTER_ID
            );

            LOGGER.debug("Initializing Kafka Ratelimit client");
            new RatelimitClient(kafkaConnection);

            LOGGER.debug("Starting Kafka connection");
            kafkaConnection.start();
        } catch (Throwable t) {
            LOGGER.error("Error initializing Kafka", t);
            System.exit(1);
        }
        LOGGER.info("Initialization done!");
    }

}
