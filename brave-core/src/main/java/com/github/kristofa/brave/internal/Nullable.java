package com.github.kristofa.brave.internal;

/**
 * Libraries such as Guice and AutoValue will process any annotation named {@code Nullable}.
 * This avoids a dependency on one of the many jsr305 jars.
 */
@java.lang.annotation.Documented
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
public @interface Nullable {
}
