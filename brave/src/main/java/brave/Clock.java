/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave;

/**
 * Epoch microseconds used for {@link brave.Span#start(long)}, {@link brave.Span#finish(long)} and
 * {@link brave.Span#annotate(long, String)}.
 *
 * <p>This should use the most precise value possible. For example, {@code gettimeofday} or
 * multiplying {@link System#currentTimeMillis} by 1000.
 *
 * <p>See <a href="https://zipkin.io/pages/instrumenting.html">Instrumenting a service</a> for
 * more.
 *
 * <p><em>Note</em>: This type is safe to implement as a lambda, or use as a method reference as it
 * is effectively a {@code FunctionalInterface}. It isn't annotated as such because the project has
 * a minimum Java language level 6.
 *
 * @since 4.0
 */
// @FunctionalInterface, except Java language level 6. Do not add methods as it will break API!
public interface Clock {

  long currentTimeMicroseconds();
}
