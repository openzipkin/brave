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

import brave.Span;

/**
 * Implements the propagation format described in {@link B3SingleFormat}.
 *
 * @deprecated Since 5.9, use {@link B3Propagation#newFactoryBuilder()} to control inject formats.
 */
@Deprecated public final class B3SinglePropagation {
  public static final Propagation.Factory FACTORY = B3Propagation.newFactoryBuilder()
    .injectFormat(B3Propagation.Format.SINGLE)
    .injectFormat(Span.Kind.CLIENT, B3Propagation.Format.SINGLE)
    .injectFormat(Span.Kind.SERVER, B3Propagation.Format.SINGLE)
    .build();
}
