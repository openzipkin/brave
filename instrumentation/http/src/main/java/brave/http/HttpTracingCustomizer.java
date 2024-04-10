/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.http;

import brave.Tracing;
import brave.TracingCustomizer;

/**
 * This allows configuration plugins to collaborate on building an instance of {@link HttpTracing}.
 *
 * <p>For example, a customizer can setup {@link HttpTracing.Builder#clientRequestParser(HttpRequestParser)
 * http parsers} without a reference to the {@link HttpTracing.Builder#Builder(Tracing) tracing
 * component}.
 *
 * <p>This also allows one object to customize both {@link Tracing}, via {@link TracingCustomizer},
 * and the http layer {@link HttpTracing}, by implementing both customizer interfaces.
 *
 * <h3>Integration examples</h3>
 *
 * <p>In practice, a dependency injection tool applies a collection of these instances prior to
 * {@link HttpTracing.Builder#build() building the tracing instance}. For example, an injected
 * {@code List<HttpTracingCustomizer>} parameter to a provider of {@link HttpTracing}.
 *
 * <p>Here are some examples, in alphabetical order:
 * <pre><ul>
 *   <li><a href="https://dagger.dev/multibindings.html">Dagger Set Multibindings</a></li>
 *   <li><a href="http://google.github.io/guice/api-docs/latest/javadoc/com/google/inject/multibindings/Multibinder.html">Guice Set Multibinder</a></li>
 *   <li><a href="https://docs.spring.io/spring/docs/current/spring-framework-reference/core.html#beans-autowired-annotation">Spring Autowired Collections</a></li>
 * </ul></pre>
 *
 * <p><em>Note</em>: This type is safe to implement as a lambda, or use as a method reference as it
 * is effectively a {@code FunctionalInterface}. It isn't annotated as such because the project has
 * a minimum Java language level 6.
 *
 * @see TracingCustomizer
 * @since 5.7
 */
// @FunctionalInterface, except Java language level 6. Do not add methods as it will break API!
public interface HttpTracingCustomizer {
  /** Use to avoid comparing against null references */
  HttpTracingCustomizer NOOP = new HttpTracingCustomizer() {
    @Override public void customize(HttpTracing.Builder builder) {
    }

    @Override public String toString() {
      return "NoopHttpTracingCustomizer{}";
    }
  };

  void customize(HttpTracing.Builder builder);
}
