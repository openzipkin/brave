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

import brave.Tracing;
import brave.baggage.BaggageField;
import brave.internal.InternalPropagation;
import brave.internal.Platform;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This manages an mutable element of {@link TraceContext#extra()} of type ({@link Extra}). The most
 * common usage is assigning {@linkplain BaggageField baggage} parsed from the request to a span.
 * However, this supports other use cases such as extended trace contexts and secondary sampling.
 *
 * <h3>Method overview</h3>
 * {@link #decorate(TraceContext)} ensures that the resulting {@link TraceContext} has an instance
 * of "extra" ({@link #<E>}), that it is associated only with the context's span ID, and that fields
 * inside "extra" considers upstream values, such as from a parent span.
 *
 * <p>{@link #create()} is used to add an instance of {@link #<E>} to an {@link
 * TraceContextOrSamplingFlags extraction result}. Concretely, this allows additional request data,
 * such as a request ID, to be inherited from a request, and possibly changed, before a trace
 * context is {@linkplain #decorate(TraceContext) decorated}.
 *
 * <h3>Integration</h3>
 * The only valid way to integrate this is via a custom {@link Propagation.Factory}. Its {@link
 * Propagation.Factory#decorate(TraceContext)} method MUST dispatch to {@link
 * #decorate(TraceContext)}.
 *
 * <p>If request state is needed, {@link Propagation#extractor(Propagation.Getter)}
 * MUST call {@link #create()} only once per request. The result should be added to
 * TraceContextOrSamplingFlags.Builder#addExtra(Object)}.
 *
 * <h3>Notes</h3>
 * <p>The reason {@link #create()} is decoupled from {@link #decorate(TraceContext)} is because
 * extra data can exist in a request even if a trace context does not. Even when a trace context
 * exists in headers, it is only used directly when {@linkplain Tracing.Builder#supportsJoin(boolean)
 * join is supported}. In other words, {@link #<E>} carries any data from the request until it can
 * be associated with a {@link TraceContext} (via {@link #decorate(TraceContext)}.
 *
 * <p>It may not be intuitive why implementations should always add
 *
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
public abstract class ExtraFactory<E extends Extra<E, F>, F extends ExtraFactory<E, F>> {
  final Object initialState;

  /**
   * @param initialState shared with all new instances of {@link #<E>} until there is a state
   * change.
   */
  protected ExtraFactory(Object initialState) {
    if (initialState == null) throw new NullPointerException("initialState == null");
    this.initialState = initialState;
  }

  /**
   * This creates an instance of {@link #<E>} while request data is visible. For example, you can
   * read headers to add fields to the result.
   *
   * <p>Integrate this with your custom {@link TraceContext.Extractor} like so:
   * <pre>{@code
   * @Override public TraceContextOrSamplingFlags extract(R request) {
   *   TraceContextOrSamplingFlags.Builder builder = delegate.extract(request).toBuilder();
   *   MyAdditionalData extra = extraFactory.create();
   *   // do something with extra
   *   builder.addExtra(extra);
   *   return builder.build();
   * }
   * }</pre>
   *
   * <p>
   * <p><em>Note</em>: It is an error to add multiple instances returned by this to a
   * {@link TraceContextOrSamplingFlags} or {@link TraceContext}
   *
   * @return a new instance which will be assigned this decorator's {@linkplain
   * ExtraFactory#initialState initial state}.
   * @see #decorate(TraceContext)
   */
  protected abstract E create();

  /**
   * This ensures an instance of {@link #<E>} includes any parent fields and is assigned to the the
   * input context.
   *
   * <p>Integrate this with your custom {@link Propagation.Factory} like so:
   * <pre>{@code
   * @Override public TraceContext decorate(TraceContext context) {
   *   TraceContext result = delegate.decorate(context);
   *   return extraFactory.decorate(result);
   * }
   * }</pre>
   *
   * @param context context to process {@link TraceContext#extra()}
   * @return the same instance if {@link TraceContext#extra()} didn't need to change, or there was
   * improper usage of this factory.
   * @see Propagation.Factory#decorate(TraceContext)
   */
  public final TraceContext decorate(TraceContext context) {
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

        if (existingIndex == -1) {
          existingIndex = i;
        } else {
          Platform.get().log("BUG: something added redundant extra instances %s", context, null);
          return context;
        }
      }
    }

    // Easiest when there is neither existing state to assign, nor need to change context.extra()
    if (claimed != null && existingIndex == -1) {
      return context;
    }

    ArrayList<Object> mutableExtraList = new ArrayList<>(context.extra());

    // If context.extra() didn't have an unclaimed extra instance, create one for this context.
    if (claimed == null) {
      claimed = create();
      if (claimed == null) {
        Platform.get().log("BUG: create() returned null", null);
        return context;
      }
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
