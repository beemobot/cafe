package gg.beemo.latte.config.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Tells {@link  gg.beemo.latte.config.Configurator} that this field is an array. The individual entries
 * in the input string are delimited by the specified delimiter. If this annotation is not present, and
 * the field is an Array type, a default delimiter of "," is assumed. If this annotation is used on a field
 * that is not an Array type, the Configurator will exit with an error.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfiguratorArray {

    /**
     * The delimiter to use to split the input string into multiple entries. Defaults to ",".
     *
     * @return The delimiter to use.
     */
    String delimiter() default ",";

}
