package gg.beemo.latte.config.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Tells {@link  gg.beemo.latte.config.Configurator} that if a value is not present
 * then default to this variable.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfiguratorDefault {

    /**
     * The default value to use when both {@link  System#getenv(String)} and
     * {@link  gg.beemo.latte.config.Configurator#get(String)} returns null.
     *
     * @return The default value of this field.
     */
    String defaultValue();

}
