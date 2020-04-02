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
package brave.internal;

import brave.baggage.CorrelationScopeDecorator;

/**
 * Dispatches methods to synchronize fields with a context such as SLF4J MDC.
 *
 * <p><em>This is internal:</em> All subtypes of {@link CorrelationScopeDecorator} are sealed
 * to this repository until we better understand implications of making this a public type.
 */
// NOTE: revert to abstract class with protected signatures if this is ever promoted to the
// brave.propagation package.
public interface CorrelationContext {
  /** Returns the string property of the specified name or {@code null}. */
  // same as BaggageContext#getValue(BaggageField, TraceContext)
  @Nullable String getValue(String name);

  /** Returns false if the update was ignored. */
  // same as BaggageContext#updateValue(BaggageField, TraceContext, String)
  boolean update(String name, @Nullable String value);
}
