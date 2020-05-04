/*
 * Copyright 2013-2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave;

import brave.baggage.BaggagePropagationCustomizer;
import brave.handler.FinishedSpanHandler;
import brave.propagation.CurrentTraceContextCustomizer;
import zipkin2.reporter.Reporter;

/**
 * This allows configuration plugins to collaborate on building an instance of {@link Tracing}.
 *
 * <p>For example a customizer can configure {@link Tracing.Builder#addFinishedSpanHandler(FinishedSpanHandler)
 * finished span handlers} without having to also configure {@link Tracing.Builder#spanReporter(Reporter)
 * span reporting}.
 *
 * <h3>Integration examples</h3>
 *
 * <p>In practice, a dependency injection tool applies a collection of these instances prior to
 * {@link Tracing.Builder#build() building the tracing instance}. For example, an injected {@code
 * List<TracingCustomizer>} parameter to a provider of {@link Tracing}.
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
 * @see BaggagePropagationCustomizer
 * @see CurrentTraceContextCustomizer
 * @since 5.7
 */
// @FunctionalInterface, except Java language level 6. Do not add methods as it will break API!
public interface TracingCustomizer {
  /** Use to avoid comparing against null references */
  TracingCustomizer NOOP = new TracingCustomizer() {
    @Override public void customize(Tracing.Builder builder) {
    }

    @Override public String toString() {
      return "NoopTracingCustomizer{}";
    }
  };

  void customize(Tracing.Builder builder);
}
