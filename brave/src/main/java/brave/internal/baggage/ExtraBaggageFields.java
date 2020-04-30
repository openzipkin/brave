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
package brave.internal.baggage;

import brave.baggage.BaggageField;
import brave.internal.Nullable;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.List;

/**
 * Holds one or more baggage fields in {@link TraceContext#extra()} or {@link
 * TraceContextOrSamplingFlags#extra()}.
 *
 * <p>We need to retain propagation state extracted from headers. However, we don't know the trace
 * identifiers, yet. In order to resolve this ordering concern, we create an object to hold extra
 * state, and defer associating it with a span ID (via {@link ExtraBaggageFieldsFactory#decorate(TraceContext)}.
 *
 * <p>The implementation of this type uses copy-on-write semantics to prevent changes in a
 * child context from affecting its parent.
 */
// We handle dynamic vs fixed state internally as it..
//  * hides generic type complexity
//  * gives us a lock not exposed to users
//  * allows findExtra(ExtraBaggageFields.class)
public final class ExtraBaggageFields {
  final State<?> internal; // compared by reference to ensure same configuration
  long traceId;
  long spanId; // guarded by stateHandler

  ExtraBaggageFields(State<?> internal) {
    this.internal = internal;
  }

  /** When true, calls to {@link #getAllFields()} cannot be cached. */
  public boolean isDynamic() {
    return internal.isDynamic();
  }

  /** The list of fields present, regardless of value. */
  public List<BaggageField> getAllFields() {
    return internal.getAllFields();
  }

  /**
   * Returns the value of the field with the specified name or {@code null} if not available.
   *
   * @see BaggageField#getValue(TraceContext)
   * @see BaggageField#getValue(TraceContextOrSamplingFlags)
   */
  @Nullable public String getValue(BaggageField field) {
    return internal.getValue(field);
  }

  /**
   * Updates a state object to include a value change.
   *
   * @param field the field that was updated
   * @param value {@code null} means remove the mapping to this field.
   * @return true implies a a change in the underlying state
   * @see BaggageField#updateValue(TraceContext, String)
   * @see BaggageField#updateValue(TraceContextOrSamplingFlags, String)
   */
  public boolean updateValue(BaggageField field, @Nullable String value) {
    return internal.updateValue(field, value);
  }

  static abstract class State<S> {
    final ExtraBaggageFieldsFactory factory;
    volatile S state; // guarded by this, copy on write, never null

    State(ExtraBaggageFieldsFactory factory, S parentState) {
      if (factory == null) throw new NullPointerException("factory == null");
      if (parentState == null) throw new NullPointerException("parentState == null");
      this.factory = factory;
      this.state = parentState;
    }

    /** @see ExtraBaggageFields#isDynamic() */
    abstract boolean isDynamic();

    /** @see ExtraBaggageFields#getAllFields() */
    abstract List<BaggageField> getAllFields();

    /** @see ExtraBaggageFields#getValue(BaggageField) */
    abstract @Nullable String getValue(BaggageField field);

    /** @see ExtraBaggageFields#updateValue(BaggageField, String) */
    abstract boolean updateValue(BaggageField field, @Nullable String value);

    /**
     * For each field in the input replace the state if the key doesn't already exist.
     *
     * <p>Note: this does not synchronize internally as it is acting on newly constructed fields
     * not yet returned to a caller.
     */
    abstract void mergeStateKeepingOursOnConflict(ExtraBaggageFields parent);
  }

  /** Fields are extracted before a context is created. We need to lazy set the context */
  boolean tryToClaim(long traceId, long spanId) {
    synchronized (internal) {
      if (this.traceId == 0L) {
        this.traceId = traceId;
        this.spanId = spanId;
        return true;
      }
      return this.traceId == traceId && this.spanId == spanId;
    }
  }

  // Implemented for equals when no baggage was extracted
  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof ExtraBaggageFields)) return false;
    return internal.equals(((ExtraBaggageFields) o).internal);
  }

  @Override public int hashCode() {
    return internal.hashCode();
  }

  static boolean equal(@Nullable Object a, @Nullable Object b) {
    return a == null ? b == null : a.equals(b); // Java 6 can't use Objects.equals()
  }
}
