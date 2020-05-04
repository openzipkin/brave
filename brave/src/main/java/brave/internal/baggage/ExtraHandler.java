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

import brave.internal.InternalPropagation;
import brave.internal.Nullable;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages a single mutable element of {@link TraceContext#extra()} per {@linkplain TraceContext
 * context} or {@link TraceContextOrSamplingFlags extraction result}: {@link #<E>}.
 *
 * <h3>Integration</h3>
 * <p>{@link TraceContext.Extractor#extract(Object)} must invoke {@link
 * #provisionExtra(TraceContextOrSamplingFlags.Builder, Object)} before returning.
 *
 * <p>{@link #ensureContainsExtra(TraceContext)} must be with a hook that operates on every new
 * trace context, such as {@link Propagation.Factory#decorate(TraceContext)}.
 *
 * <h3>Notes</h3>
 * <p>If your data is not mutable, and is constant through the trace, do not use this. Instead, add
 * your own type via {@link TraceContextOrSamplingFlags.Builder#addExtra(Object)} as the tracer will
 * copy it down to children by default.
 *
 * <p>Even though {@link #<E>} is mutable, it must be copy-on-write internally. This type manages
 * state forking that ensures that updates to child spans are invisible to their parents or
 * siblings.
 *
 * @param <E> They type of {@link Extra} managed by this factory. Must be a final class. The type is
 * typically package private to avoid accidental interference.
 * @param <F> An instance of this factory. {@link #<E>} should be associated with only one factory.
 */
public abstract class ExtraHandler<E extends Extra<E, F>, F extends ExtraHandler<E, F>> {
  final Object initialState;

  /**
   * @param initialState shared with all new instances of {@link #<E>} until there is a state
   * change.
   */
  protected ExtraHandler(Object initialState) {
    if (initialState == null) throw new NullPointerException("initialState == null");
    this.initialState = initialState;
  }

  /**
   * Creates a new instance of {@link #<E>}, with this factory's initial state.
   *
   * <p>This is only called in two scenarios:
   *
   * <p>During extraction, {@link #provisionExtra(TraceContextOrSamplingFlags.Builder, Object)}
   * invokes this method with a non-{@code null} {@code request} parameter.
   *
   * <p>For a new local root, {@link #ensureContainsExtra(TraceContext)} invokes this method with a
   * {@code null} {@code request} parameter.
   *
   * @param request possibly {@code null} request
   * @return a new instance which will be assigned this decorator's {@linkplain
   * ExtraHandler#initialState initial state}.
   * @see #provisionExtra(TraceContextOrSamplingFlags.Builder, Object)
   * @see #ensureContainsExtra(TraceContext)
   */
  // protected to prevent unwanted callers from creating multiple instances per context
  protected abstract E newExtra(@Nullable Object request);

  /**
   * Provisions a new instance of {@link #<E>} and adds it to the builder. Integrate this via {@link
   * TraceContext.Extractor#extract(Object)}.
   *
   * @param request the request parameter of {@link TraceContext.Extractor#extract(Object)}
   * @return the instance already added to the builder.
   * @see #newExtra(Object)
   * @see #ensureContainsExtra(TraceContext)
   * @see TraceContext.Extractor#extract(Object)
   */
  // This operates on the builder directly to allow us to hide newExtra and potential bugs
  public final E provisionExtra(TraceContextOrSamplingFlags.Builder builder, Object request) {
    if (builder == null) throw new NullPointerException("builder == null");
    if (request == null) throw new NullPointerException("request == null");
    E result = newExtra(request);
    if (result == null) throw new RuntimeException("BUG: provision(request) returned null");
    builder.addExtra(result);
    return result;
  }

  /**
   * Backfills an instance of {@link #<E>} to {@link TraceContext#extra()} if needed, after ensuring
   * it is associated with the {@code context}. Integrate this via a hook that operates on every new
   * trace context, such as {@link Propagation.Factory#decorate(TraceContext)}.
   *
   * @see #newExtra(Object)
   * @see #provisionExtra(TraceContextOrSamplingFlags.Builder, Object)
   * @see Propagation.Factory#decorate(TraceContext)
   */
  public final TraceContext ensureContainsExtra(TraceContext context) {
    long traceId = context.traceId(), spanId = context.spanId();

    E claimed = null;
    int existingIndex = -1;
    for (int i = 0, length = context.extra().size(); i < length; i++) {
      Object next = context.extra().get(i);
      if (next instanceof Extra) {
        Extra nextExtra = (Extra) next;
        // Don't interfere with other instances or subtypes
        if (nextExtra.factory != this) continue;

        if (claimed == null && nextExtra.tryToClaim(traceId, spanId)) {
          claimed = (E) nextExtra;
          continue;
        }

        existingIndex = i;
      }
    }

    // Easiest when there is neither existing state to assign, nor need to change context.extra()
    if (claimed != null && existingIndex == -1) {
      return context;
    }

    ArrayList<Object> mutableExtraList = new ArrayList<>(context.extra());

    // If context.extra() didn't have an unclaimed extra instance, create one for this context.
    if (claimed == null) {
      claimed = newExtra(null);
      claimed.tryToClaim(traceId, spanId);
      mutableExtraList.add(claimed);
    }

    if (existingIndex != -1) {
      E existing = (E) mutableExtraList.remove(existingIndex);

      // If the claimed extra instance was new or had no changes, simply assign existing to it 
      if (claimed.state == initialState) {
        claimed.state = existing.state;
      } else if (existing.state != initialState) {
        claimed.mergeStateKeepingOursOnConflict(existing);
      }
    }

    return contextWithExtra(context, Collections.unmodifiableList(mutableExtraList));
  }

  // TODO: this is internal. If this ever expose it otherwise, this should use Lists.ensureImmutable
  TraceContext contextWithExtra(TraceContext context, List<Object> extra) {
    return InternalPropagation.instance.withExtra(context, extra);
  }
}
