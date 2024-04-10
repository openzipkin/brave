/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.internal;

/**
 * Dispatches methods to synchronize fields with a context such as SLF4J MDC.
 *
 * <p><em>This is internal:</em> All subtypes of {@link CorrelationContext} are sealed
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
