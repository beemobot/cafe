package gg.beemo.latte.config;

import gg.beemo.latte.config.annotations.ConfiguratorIgnore;
import org.jetbrains.annotations.Nullable;
import java.io.File;

public interface Configurator {

    /**
     * Creates a new {@link Configurator} instance that allows the mirroring
     * of environment variables onto the instance. The configuration values will
     * be read from a `.env` file in the current PWD.
     *
     * @return A new {@link Configurator} instance.
     */
    static Configurator create() {
        return from(".env");
    }

    /**
     * Creates a new {@link Configurator} instance that allows the mirroring
     * of environment variables onto the instance.
     *
     * @param path  The path of the file containing the environment variables.
     * @return      A new {@link Configurator} instance.
     */
    static Configurator from(String path) {
        return from(new File(path));
    }

    /**
     * Creates a new {@link Configurator} instance that allows the mirroring
     * of environment variables onto the instance.
     *
     * @param file  The file containing the environment variables.
     * @return      A new {@link Configurator} instance.
     */
    static Configurator from(File file) {
        return new ConfiguratorImpl(file);
    }

    /**
     * Adds an adapter that allows {@link Configurator} to be able to automatically
     * transform specific environment key-pairs into the type it should be.
     *
     * @param clazz     The class that this adapter should transform.
     * @param adapter   The adapter to use when transforming the pair into the class.
     */
    static void addAdapter(Class<?> clazz, ConfiguratorAdapter<?>  adapter) {
        ConfiguratorImpl.addAdapter(clazz, adapter);
    }

    /**
     * Mirrors all the environment variables into the specific fields that either matches
     * the following: name (whether it'd be done through {@link gg.beemo.latte.config.annotations.ConfiguratorRename} or
     * field name). <br><br>
     *
     * The mirroring happens case-insensitive which allows static variables to follow the all-capitalization
     * convention regardless of whether the .env declares it as non-capital.<br><br>
     *
     * You can tell the mirroring process to ignore a field by annotating {@link ConfiguratorIgnore}
     * onto the field itself, and it will ignore it.
     *
     * @throws IllegalArgumentException This happens when the field's class couldn't be mirrored, use {@link ConfiguratorAdapter}.
     * @throws NumberFormatException    This happens when the field's variable cannot be convered into an integer.
     * @param toClass The class to mirror the fields.
     */
    void mirror(Class<?> toClass);

    /**
     * Gets the environment variable's value, will default to
     * {@link  System#getenv(String)} if the variable doesn't exist in
     * the .env.
     *
     * @param key   The key of the variable.
     * @return      The value of the variable as a string.
     */
    @Nullable
    String get(String key);

    /**
     * Enables or disables the functionality that tells {@link Configurator} to
     * search through the {@link  System#getenv(String)} if the value of the key is
     * not there.
     *
     * @param allow Should we allow the defaulting functionality?
     * @return      {@link Configurator} for chain-calling methods.
     */
    Configurator allowDefaultToSystemEnvironment(boolean allow);

}
