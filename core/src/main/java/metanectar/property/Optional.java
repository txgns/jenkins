package metanectar.property;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author Paul Sandoz
 */
@Retention(RUNTIME)
@Target({TYPE, METHOD})
@Documented
public @interface Optional {
}