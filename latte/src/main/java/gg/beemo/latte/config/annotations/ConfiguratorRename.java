package gg.beemo.latte.config.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Tells {@link  gg.beemo.latte.config.Configurator} that the actual key name of
 * this field is not the field name but another key name. Honesty is the best policy.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfiguratorRename {

    /**
     * The actual key name of this field in the environment file.
     *
     * @return The key name of this field.
     */
    String actualKeyName();

}
