package gg.beemo.vanilla;

import gg.beemo.latte.config.annotations.ConfiguratorDefault;

public class Config {

    @ConfiguratorDefault(defaultValue = "localhost:9094")
    public static String KAFKA_HOST;

}
