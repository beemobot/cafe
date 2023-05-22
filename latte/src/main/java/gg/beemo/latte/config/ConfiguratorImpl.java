package gg.beemo.latte.config;

import gg.beemo.latte.config.annotations.ConfiguratorIgnore;
import gg.beemo.latte.config.annotations.ConfiguratorRename;
import gg.beemo.latte.config.annotations.ConfiguratorRequired;
import gg.beemo.latte.logging.LoggerKt;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ConfiguratorImpl implements Configurator {

    private static final Map<Class<?>, ConfiguratorAdapter<?>> adapters = new HashMap<>();
    private static final Set<Field> redactedFields = new HashSet<>();

    private final Map<String, String> environments = new ConcurrentHashMap<>();
    private boolean allowDefaultToSystemEnvironment = true;
    private static final Logger LOGGER = LoggerKt.getLogger(ConfiguratorImpl.class);

    /**
     * Creates a new {@link ConfiguratorImpl} with the values of
     * the {@link File} specified.
     *
     * @param file The file to read.
     */
    public ConfiguratorImpl(File file) {
        if (!file.exists()) {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(file.toPath())) {
            reader.lines()
                    .filter(line -> line.contains("=") && !line.startsWith("#"))
                    .map(line -> line.split("=", 2))
                    .forEach(array -> {
                        if (array.length < 2) {
                            environments.put(array[0].toLowerCase(), "");
                            return;
                        }

                        environments.put(array[0].toLowerCase(), array[1]);
                    });
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    @Override
    @Nullable
    public String get(String key) {
        if (environments.containsKey(key.toLowerCase())) {
            return environments.get(key.toLowerCase());
        }

        if (allowDefaultToSystemEnvironment) {
            return System.getenv(key);
        }

        return null;
    }

    @Override
    public Configurator allowDefaultToSystemEnvironment(boolean allow) {
        this.allowDefaultToSystemEnvironment = allow;
        return this;
    }

    @Override
    public void mirror(Class<?> toClass) {
        Arrays.stream(toClass.getDeclaredFields()).forEachOrdered(field -> {
            if (field.isAnnotationPresent(ConfiguratorIgnore.class)) {
                return;
            }

            String name = field.getName();
            if (field.isAnnotationPresent(ConfiguratorRename.class)) {
                name = field.getAnnotation(ConfiguratorRename.class).actualKeyName();
            }

            String value = get(name);
            if (value == null) {
                if (field.isAnnotationPresent(ConfiguratorRequired.class)) {
                    LOGGER.error("Configurator variable {} was not provided!", name);
                    System.exit(1);
                }
                return;
            }
            String logValue = redactedFields.contains(field) ? "*****<REDACTED>*****" : value;
            LOGGER.debug("Setting variable {}={}", name, logValue);

            if (!field.trySetAccessible()) {
                LOGGER.error("Configurator failed to mark field {} as accessible", field.getName());
                System.exit(1);
            }

            try {
                Class<?> type = field.getType();

                if (isTypeEither(type, Boolean.class, boolean.class)) {
                    field.setBoolean(field, Boolean.parseBoolean(value));
                } else if (isTypeEither(type, Integer.class, int.class)) {
                    field.setInt(field, Integer.parseInt(value));
                } else if (isTypeEither(type, Long.class, long.class)) {
                    field.setLong(field, Long.parseLong(value));
                } else if (isTypeEither(type, Double.class, double.class)) {
                    field.setDouble(field, Double.parseDouble(value));
                } else if (type.equals(String.class)) {
                    field.set(field, value);
                } else if (adapters.containsKey(type)) {
                    field.set(field, adapters.get(type).transform(name, value, this));
                } else {
                    LOGGER.error("Configurator failed to find a proper transformer or adapter for a field. " +
                            "Please use ConfiguratorIgnore to ignore otherwise add an adapter via Configurator.addAdapter(...). [field={}]", field.getName());
                    System.exit(1);
                }

            } catch (IllegalAccessException | NumberFormatException e) {
                LOGGER.error("Configurator encountered an throwable while attempting to convert a field. [field={}]", field.getName(), e);
                System.exit(1);
            }

        });
    }

    /**
     * A convenient helper method to identify whether the type specified is
     * either of these two. This is used against generic types which always has two
     * types of classes.
     *
     * @param type    The type to compare against either.
     * @param typeOne The first type to compare.
     * @param typeTwo The second type to compare.
     * @return Is the type matches either of them?
     */
    private boolean isTypeEither(Class<?> type, Class<?> typeOne, Class<?> typeTwo) {
        return type.equals(typeOne) || type.equals(typeTwo);
    }

    /**
     * Adds an adapter that allows {@link Configurator} to be able to automatically
     * transform specific environment key-pairs into the type it should be.
     *
     * @param clazz   The class that this adapter should transform.
     * @param adapter The adapter to use when transforming the pair into the class.
     */
    public static void addAdapter(Class<?> clazz, ConfiguratorAdapter<?> adapter) {
        adapters.put(clazz, adapter);
    }
    /**
     * Marks a field as redacted, meaning its value will not be logged during initialization.
     *
     * @param field The Field to redact in logs.
     */
    public static void addRedactedField(Field field) {
        redactedFields.add(field);
    }
}
