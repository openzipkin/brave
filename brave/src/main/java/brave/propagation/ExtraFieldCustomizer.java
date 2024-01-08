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
package brave.propagation;

import brave.baggage.BaggagePropagationCustomizer;

/**
 * @since 5.7
 * @deprecated Since 5.11 use {@link BaggagePropagationCustomizer}
 */
@Deprecated public interface ExtraFieldCustomizer {
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
