package gg.beemo.latte.config.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Tells the {@link  gg.beemo.latte.config.Configurator} to not log this
 * field's value upon initialization.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfiguratorRedacted {
}
