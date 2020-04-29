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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
public final class ExtraBaggageFields {
  public static Factory newFactory(List<BaggageHandler<?>> handlers) {
    if (handlers == null) throw new NullPointerException("handlers == null");
    if (handlers.isEmpty()) throw new NullPointerException("handlers are empty");
    return new ExtraBaggageFieldsFactory(handlers.toArray(new BaggageHandler[0]));
  }

  public interface Factory {
    ExtraBaggageFields create();

    ExtraBaggageFields create(ExtraBaggageFields parent);

    TraceContext decorate(TraceContext context);
  }

  /** The list of fields present, regardless of value. */
  public List<BaggageField> getAllFields() {
    if (!hasDynamicFields) return fixedFieldList;
    List<BaggageField> result = new ArrayList<>(fixedFieldList);
    Object[] stateArray = this.stateArray;
    for (int i = 0, length = handlers.length; i < length; i++) {
      BaggageHandler handler = handlers[i];
      if (!handler.isDynamic()) continue;
      result.addAll(handler.currentFields(stateArray != null ? stateArray[i] : null));
    }
    return Collections.unmodifiableList(result);
  }

  /**
   * Returns the value of the field with the specified name or {@code null} if not available.
   *
   * @see BaggageField#getValue(TraceContext)
   */
  @Nullable public String getValue(BaggageField field) {
    int index = indexOf(field);
    if (index == -1) return null;
    Object[] stateArray = this.stateArray;
    if (stateArray == null) return null;
    Object state = stateArray[index];
    return state != null ? handlers[index].getValue(field, state) : null;
  }

  /**
   * Replaces the value of the field with the specified key, ignoring if not configured.
   *
   * @see BaggageField#updateValue(TraceContext, String)
   */
  public boolean updateValue(BaggageField field, @Nullable String value) {
    int index = indexOf(field);
    if (index == -1) return false;

    BaggageHandler handler = handlers[index];
    synchronized (this) {
      Object[] stateArray = this.stateArray;
      if (stateArray == null) {
        if (value == null) return false;
        stateArray = new Object[handlers.length];
      }

      Object state = handler.updateState(stateArray[index], field, value);

      if (equal(state, stateArray[index])) return false;

      // this is the copy-on-write part
      stateArray = Arrays.copyOf(stateArray, stateArray.length);
      stateArray[index] = state;
      this.stateArray = stateArray;
      return true;
    }
  }

  /**
   * Sets the {@linkplain #<S> state of the handler}, possibly to {@code null}.
   *
   * <p><em>Note:</em> The result must be treated read-only even if it a mutable object.
   *
   * <p>The result returned reflects the current association to the given handler. Due to
   * copy-on-write, a future call may return a different value.
   *
   * @param handler same reference as passed to {@link #newFactory(BaggageHandler[])}
   * @param state {@code null} clears the state for the handler.
   * @see #getState(BaggageHandler)
   */
  @Nullable public <S> void putState(BaggageHandler<S> handler, @Nullable S state) {
    if (handler == null) throw new NullPointerException("handler == null");

    int index = indexOf(handler);
    if (index == -1) return;

    synchronized (this) {
      Object[] stateArray = this.stateArray;
      if (stateArray == null) {
        if (state == null) return;
        stateArray = new Object[handlers.length];
      }

      if (equal(state, stateArray[index])) return;

      // this is the copy-on-write part
      stateArray = Arrays.copyOf(stateArray, stateArray.length);
      stateArray[index] = state;
      this.stateArray = stateArray;
    }
  }

  /**
   * Gets the possibly {@code null} {@linkplain #<S> state of the handler}.
   *
   * <p><em>Note:</em> The result must be treated read-only even if it a mutable object.
   *
   * <p>The result returned reflects the current association to the given handler. Due to
   * copy-on-write, a future call may return a different value.
   *
   * <p>Unlike {@link #getValue(BaggageField)}, the result of this may not be a {@link String} and
   * may be mutable. It may be shared across multiple fields and possibly multiple trace contexts.
   * Do not mutate the result as you can corrupt data.
   *
   * @param handler same reference as passed to {@link #newFactory(List)}
   * @return the current state assigned to the handler or {@code null} if unavailable.
   * @see #putState(int, Object)
   */
  @Nullable public <S> S getState(BaggageHandler<S> handler) {
    if (handler == null) throw new NullPointerException("handler == null");

    int index = indexOf(handler);
    if (index == -1) return null;

    Object[] stateArray = this.stateArray;
    return stateArray != null ? (S) stateArray[index] : null;
  }

  final BaggageHandler[] handlers;
  final List<BaggageField> fixedFieldList;

  final boolean hasDynamicFields;

  volatile Object[] stateArray; // guarded by this, copy on write
  long traceId, spanId; // guarded by this

  ExtraBaggageFields(BaggageHandler[] handlers) {
    this.handlers = handlers;
    List<BaggageField> constantFields = new ArrayList<>();
    boolean hasDynamicFields = false;
    for (BaggageHandler handler : handlers) {
      if (!handler.isDynamic()) {
        constantFields.addAll(handler.currentFields(null));
      } else {
        hasDynamicFields = true;
      }
    }
    this.hasDynamicFields = hasDynamicFields;
    this.fixedFieldList = Collections.unmodifiableList(constantFields);
  }

  ExtraBaggageFields(ExtraBaggageFields parent, BaggageHandler[] handlers) {
    this(handlers);
    checkSameHandlers(parent);
    this.stateArray = parent.stateArray;
  }

  boolean putState(int index, @Nullable Object state) {
    Object[] stateArray = this.stateArray;
    if (stateArray == null) {
      stateArray = new Object[handlers.length];
      stateArray[index] = state;
    } else if (equal(state, stateArray[index])) {
      return false;
    } else { // this is the copy-on-write part
      stateArray = Arrays.copyOf(stateArray, stateArray.length);
      stateArray[index] = state;
    }
    this.stateArray = stateArray;
    return true;
  }

  void checkSameHandlers(ExtraBaggageFields predefinedParent) {
    if (!Arrays.equals(handlers, predefinedParent.handlers)) {
      throw new IllegalStateException(
        String.format("Mixed name configuration unsupported: found %s, expected %s",
          Arrays.toString(handlers), Arrays.toString(predefinedParent.handlers))
      );
    }
  }

  int indexOf(BaggageHandler handler) {
    for (int i = 0, length = handlers.length; i < length; i++) {
      if (handlers[i].equals(handler)) return i;
    }
    return -1;
  }

  int indexOf(BaggageField field) {
    for (int i = 0, length = handlers.length; i < length; i++) {
      if (handlers[i].handlesField(field)) return i;
    }
    return -1;
  }

  @Nullable Object getState(int index) {
    Object[] stateArray = this.stateArray;
    if (stateArray == null || index >= stateArray.length) return null;
    return stateArray[index];
  }

  /**
   * For each field in the input replace the state if the key doesn't already exist.
   *
   * <p>Note: this does not synchronize internally as it is acting on newly constructed fields
   * not yet returned to a caller.
   */
  void putAllIfAbsent(ExtraBaggageFields parent) {
    checkSameHandlers(parent);
    Object[] parentStateArray = parent.stateArray;
    if (parentStateArray == null) return;
    for (int i = 0; i < parentStateArray.length; i++) {
      if (parentStateArray[i] != null && getState(i) == null) { // extracted wins vs parent
        putState(i, parentStateArray[i]);
      }
    }
  }

  /** Fields are extracted before a context is created. We need to lazy set the context */
  boolean tryToClaim(long traceId, long spanId) {
    synchronized (this) {
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
    ExtraBaggageFields that = (ExtraBaggageFields) o;
    return Arrays.equals(handlers, that.handlers) && Arrays.equals(stateArray, that.stateArray);
  }

  @Override public int hashCode() {
    int h = 1000003;
    h ^= Arrays.hashCode(handlers);
    h *= 1000003;
    h ^= Arrays.hashCode(stateArray);
    return h;
  }

  static boolean equal(@Nullable Object a, @Nullable Object b) {
    return a == null ? b == null : a.equals(b); // Java 6 can't use Objects.equals()
  }
}
