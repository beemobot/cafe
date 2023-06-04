package gg.beemo.latte.config;

import gg.beemo.latte.config.annotations.*;
import gg.beemo.latte.logging.LoggerKt;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ConfiguratorImpl implements Configurator {

    private static final Map<Class<?>, ConfiguratorAdapter<?>> adapters = new HashMap<>();

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
            if (value.equals("null")) {
                value = null;
            }

            if (!field.trySetAccessible()) {
                LOGGER.error("Configurator failed to mark field {} as accessible", field.getName());
                System.exit(1);
            }

            ConfiguratorArray arrayAnnotation = field.getAnnotation(ConfiguratorArray.class);
            String[] arrayValues = null;
            Class<?> valueType = field.getType();
            boolean isArray = valueType.isArray();

            if (isArray) {

                valueType = valueType.getComponentType();
                String valueDelimiter = arrayAnnotation != null ? arrayAnnotation.delimiter() : ",";
                arrayValues = value != null ? value.split(valueDelimiter) : new String[]{};

            } else if (arrayAnnotation != null) {
                LOGGER.error("Field {} is annotated with @ConfiguratorArray even though it's not an Array type", field.getName());
                System.exit(1);
            }

            boolean isRedacted = field.isAnnotationPresent(ConfiguratorRedacted.class);
            String logValue = isRedacted ? "*****<REDACTED>*****" : isArray ? String.join(",", arrayValues) : value;
            LOGGER.debug("Setting variable {}={}", name, logValue);

            try {

                if (!isArray) {
                    Object parsedValue = parseValueOfType(field, valueType, name, value);
                    field.set(field, parsedValue);
                } else {
                    Object array = Array.newInstance(valueType, arrayValues.length);
                    for (int i = 0; i < arrayValues.length; i++) {
                        Object parsedValue = parseValueOfType(field, valueType, name, arrayValues[i]);
                        Array.set(array, i, parsedValue);
                    }
                    field.set(field, array);
                }

            } catch (IllegalAccessException | NumberFormatException e) {
                LOGGER.error("Configurator encountered an throwable while attempting to convert a field. [field={}]", field.getName(), e);
                System.exit(1);
            }

        });
    }

    /**
     * Parses a given String value into the type given by the type parameter.
     *
     * @param field The field this value conversion is for.
     * @param type  The type to convert the given value into.
     * @param name  The name of the field this value conversion is for.
     * @param value The actual String value to convert.
     * @return The converted value.
     * @throws NumberFormatException if the value should be converted to a number type but is not a well-formed number.
     */
    private Object parseValueOfType(Field field, Class<?> type, String name, String value) throws NumberFormatException {
        if (type.equals(String.class)) {
            return value;
        } else if (isTypeEither(type, Boolean.class, boolean.class)) {
            return Boolean.parseBoolean(value);
        } else if (isTypeEither(type, Integer.class, int.class)) {
            return Integer.parseInt(value);
        } else if (isTypeEither(type, Long.class, long.class)) {
            return Long.parseLong(value);
        } else if (isTypeEither(type, Float.class, float.class)) {
            return Float.parseFloat(value);
        } else if (isTypeEither(type, Double.class, double.class)) {
            return Double.parseDouble(value);
        } else if (adapters.containsKey(type)) {
            return adapters.get(type).transform(name, value, this);
        }

        LOGGER.error("Configurator failed to find a proper adapter for the field {}. " +
                "Use either ConfiguratorIgnore to ignore this field, or add a matching adapter via Configurator.addAdapter(...).", field.getName());
        System.exit(1);
        return null; // Required
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

}
