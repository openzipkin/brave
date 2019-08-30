/*
 * Copyright 2013-2019 The OpenZipkin Authors
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
package brave.propagation;

import brave.Tracing;
import brave.TracingCustomizer;

/**
 * This allows configuration plugins to collaborate on building an instance of {@link
 * ExtraFieldPropagation.Factory}.
 *
 * <p>For example, a customizer can {@link ExtraFieldPropagation.FactoryBuilder#addField(String)
 * add an extra field field} without affecting the {@link ExtraFieldPropagation#newFactoryBuilder(Propagation.Factory)
 * trace propagation format}.
 *
 * <p>This also allows one object to customize both {@link Tracing}, via {@link TracingCustomizer},
 * and extra fields {@link ExtraFieldPropagation}, by implementing both customizer interfaces.
 *
 * <h3>Integration examples</h3>
 *
 * <p>In practice, a dependency injection tool applies a collection of these instances prior to
 * {@link ExtraFieldPropagation.FactoryBuilder#build() building the tracing instance}. For example,
 * an injected {@code List<ExtraFieldPropagationCustomizer>} parameter to a provider of {@link
 * Propagation.Factory}.
 *
 * <p>Here are some examples, in alphabetical order:
 * <pre><ul>
 *   <li><a href="https://dagger.dev/multibindings.html">Dagger Set Multibindings</a></li>
 *   <li><a href="http://google.github.io/guice/api-docs/latest/javadoc/com/google/inject/multibindings/Multibinder.html">Guice Set Multibinder</a></li>
 *   <li><a href="https://docs.spring.io/spring/docs/current/spring-framework-reference/core.html#beans-autowired-annotation">Spring Autowired Collections</a></li>
 * </ul></pre>
 *
 * @see TracingCustomizer
 * @since 5.7
 */
public interface ExtraFieldCustomizer {
  /** Use to avoid comparing against null references */
  ExtraFieldCustomizer NOOP = new ExtraFieldCustomizer() {
    @Override public void customize(ExtraFieldPropagation.FactoryBuilder builder) {
    }

    @Override public String toString() {
      return "NoopExtraFieldCustomizer{}";
    }
  };

  void customize(ExtraFieldPropagation.FactoryBuilder builder);
}
