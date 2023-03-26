package gg.beemo.latte.config;

public interface ConfiguratorAdapter<T> {

    /**
     * Transforms the specific environment key-value into the class
     * it is destined to become.
     *
     * @param key          The key name of this environment pair.
     * @param value        The value of this environment pair.
     * @param configurator The {@link Configurator} instance.
     * @return The expected return value from transformation.
     */
    T transform(String key, String value, Configurator configurator);

}
