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
package brave.baggage;

import brave.propagation.Propagation;

/**
 * This allows configuration plugins to collaborate on building an instance of {@link
 * BaggagePropagation.Factory}.
 *
 * <p>For example, a customizer can {@link BaggagePropagation.FactoryBuilder#add(BaggagePropagationConfig)
 * add a baggage field} without affecting the {@link BaggagePropagation#newFactoryBuilder(Propagation.Factory)
 * trace propagation format}.
 *
 * <p>This also allows one object to customize both {@linkplain BaggagePropagation baggage}
 * and {@link CorrelationScopeDecorator correlation integration}, by implementing both customizer
 * interfaces.
 *
 * <h3>Integration examples</h3>
 *
 * <p>In practice, a dependency injection tool applies a collection of these instances prior to
 * {@link BaggagePropagation.FactoryBuilder#build() building the baggage propagation instance}. For
 * example, an injected {@code List<BaggagePropagationCustomizer>} parameter to a provider of {@link
 * Propagation.Factory}.
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
 * @see CorrelationScopeCustomizer
 * @see BaggagePropagationConfig
 * @since 5.11
 */
// @FunctionalInterface, except Java language level 6. Do not add methods as it will break API!
public interface BaggagePropagationCustomizer {
  /** Use to avoid comparing against null references */
  BaggagePropagationCustomizer NOOP = new BaggagePropagationCustomizer() {
    @Override public void customize(BaggagePropagation.FactoryBuilder builder) {
    }

    @Override public String toString() {
      return "NoopBaggagePropagationCustomizer{}";
    }
  };

  void customize(BaggagePropagation.FactoryBuilder builder);
}
