package brave.internal;

import java.lang.annotation.RetentionPolicy;

/**
 * AutoValue will process any annotation named {@code Nullable}. This avoids a dependency on one of
 * the many jsr305 jars.
 */
@java.lang.annotation.Documented
@java.lang.annotation.Retention(RetentionPolicy.SOURCE)
public @interface Nullable {
}
