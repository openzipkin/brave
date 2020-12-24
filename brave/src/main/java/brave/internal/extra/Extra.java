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
package brave.internal.extra;

import brave.internal.Nullable;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.Arrays;

/**
 * Holds extended state in {@link TraceContext#extra()} or {@link TraceContextOrSamplingFlags#extra()}.
 *
 * <p>Implementations copy-on-write when changing {@linkplain #state} to prevent a child context
 * from affecting its parent.
 *
 * @param <E> Use a final type as otherwise tools like {@link TraceContext#findExtra(Class)} will
 *            not work. In most cases, the type should be package private.
 * @param <F> The factory that {@link ExtraFactory#create() creates} this instance.
 */
// We handle dynamic vs fixed state internally as it..
//  * hides generic type complexity
//  * gives us a lock not exposed to users
//  * allows findExtra(ExtraFieldsSubtype.class)
public abstract class Extra<E extends Extra<E, F>, F extends ExtraFactory<E, F>> {
  protected final F factory; // compared by reference to ensure same configuration

  /**
   * Updates like {@link #tryToClaim(long, long)} lock on this object, as should any non-atomic
   * {@link #state} updates.
   */
  protected final Object lock = new Object();
  /**
   * Lock on {@link #lock} when comparing existing values for a state update that happens after
   * {@link #mergeStateKeepingOursOnConflict(Extra)}.
   */
  protected volatile Object state;
  long traceId;
  long spanId; // guarded by lock

  protected Extra(F factory) {
    if (factory == null) throw new NullPointerException("factory == null");
    this.factory = factory;
    this.state = factory.initialState;
  }

  /**
   * {@code this} instance is newly provisioned, but may have non-empty state. Compare its
   * {@linkplain Extra#state state} vs theirs and copy their properties.
   *
   * <p>As typical in this type, treat state copy-on-write, but do not add overhead unnecessarily.
   * For example, if properties are the same, return without copying.
   *
   * <p>Ex 1: If state is a map, and ours includes {@code A -> 1, B -> 2} and so does theirs,
   * do not make a copy, just return either.
   * <p>Ex 1: If state is a map, and ours includes {@code A -> 1, B -> 2} and theirs
   * includes {@code A -> 2, D -> 1}, create a new state of {@code A -> 1, B -> 2, D -> 1}.
   *
   * <p><em>Note</em>: This operation does not need to {@linkplain #lock lock} as long as changes
   * happen before updating {@link #state}. See <a href="https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/package-summary.html#MemoryVisibility">MemoryVisibility</a>
   * for details on "happens before" and volatile fields.
   */
  protected abstract void mergeStateKeepingOursOnConflict(E that);

  /** Fields are extracted before a context is created. We need to lazy set the context */
  final boolean tryToClaim(long traceId, long spanId) {
    synchronized (lock) {
      if (this.traceId == 0L) {
        this.traceId = traceId;
        this.spanId = spanId;
        return true;
      }
      return this.traceId == traceId && this.spanId == spanId;
    }
  }

  /**
   * Only {@linkplain #state state} is used in {@link #equals(Object)}. For example, return {@link
   * Arrays#equals(Object[], Object[])}, if the state is {@code Object[]}.
   *
   * @param thatState {@linkplain #state state} being compared. It is from the same type as this.
   */
  protected abstract boolean stateEquals(Object thatState);

  /**
   * Only {@linkplain #state state} is used in {@link #hashCode()}. For example, return {@link
   * Arrays#hashCode(Object[])}, if the state is {@code Object[]}.
   */
  protected abstract int stateHashCode();

  /**
   * Only {@linkplain #state state} is used in {@link #hashCode()}. For example, return {@link
   * Arrays#toString(Object[])}, if the state is {@code Object[]}.
   */
  protected abstract String stateString();

  // Implemented for equals when nothing was extracted
  @Override public final boolean equals(Object o) {
    if (o == this) return true;
    // Extra fields should be equal on exact type, not subtype.
    // Otherwise, consolidation doesn't work
    if (!getClass().isInstance(o)) return false;
    return stateEquals(((E) o).state);
  }

  @Override public final int hashCode() {
    return stateHashCode();
  }

  @Override public final String toString() {
    return getClass().getSimpleName() + "{" + stateString() + "}";
  }

  static boolean equal(@Nullable Object a, @Nullable Object b) {
    return a == null ? b == null : a.equals(b); // Java 6 can't use Objects.equals()
  }
}
