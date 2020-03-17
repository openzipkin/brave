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
package brave.propagation.w3c;

import brave.propagation.Propagation.Getter;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.propagation.w3c.TraceContextPropagation.Extra;
import java.util.Collections;
import java.util.List;

import static brave.propagation.B3SingleFormat.parseB3SingleFormat;

// TODO: this class has no useful tests wrt traceparent yet
final class TraceContextExtractor<C, K> implements Extractor<C> {
  final Getter<C, K> getter;
  final K traceparentKey, tracestateKey;
  final TracestateFormat tracestateFormat;
  final B3SingleFormatHandler handler = new B3SingleFormatHandler();

  TraceContextExtractor(TraceContextPropagation<K> propagation, Getter<C, K> getter) {
    this.getter = getter;
    this.traceparentKey = propagation.traceparentKey;
    this.tracestateKey = propagation.tracestateKey;
    this.tracestateFormat = new TracestateFormat(propagation.stateName);
  }

  @Override public TraceContextOrSamplingFlags extract(C carrier) {
    if (carrier == null) throw new NullPointerException("carrier == null");
    String traceparent = getter.get(carrier, traceparentKey);
    if (traceparent == null) return EMPTY;

    // TODO: add link that says tracestate itself is optional
    String tracestate = getter.get(carrier, tracestateKey);
    if (tracestate == null) {
      // NOTE: we may not want to pay attention to the sampled flag. Since it conflates
      // not-yet-sampled with sampled=false, implementations that always set flags to -00 would
      // never be traced!
      //
      // NOTE: We are required to use the same trace ID, there's some vagueness about the parent
      // span ID. Ex we don't know if upstream are sending to the same system or not, when we can't
      // read the tracestate header. Trusting the span ID (traceparent calls the span ID parent-id)
      // could result in a headless trace.
      TraceContext maybeUpstream = TraceparentFormat.parseTraceparentFormat(traceparent);
      return TraceContextOrSamplingFlags.newBuilder()
        .context(maybeUpstream)
        .extra(DEFAULT_EXTRA) // marker for outbound propagation
        .build();
    }

    CharSequence otherEntries = tracestateFormat.parseAndReturnOtherEntries(tracestate, handler);

    List<Object> extra;
    if (otherEntries == null) {
      extra = DEFAULT_EXTRA;
    } else {
      Extra e = new Extra();
      e.otherEntries = otherEntries;
      extra = Collections.singletonList(e);
    }

    TraceContext context = handler.context;
    if (context == null) {
      if (extra == DEFAULT_EXTRA) return EMPTY;
      return TraceContextOrSamplingFlags.newBuilder()
        .extra(extra)
        .samplingFlags(SamplingFlags.EMPTY)
        .build();
    }
    return TraceContextOrSamplingFlags.newBuilder().context(context).extra(extra).build();
  }

  static final class B3SingleFormatHandler implements TracestateFormat.Handler {
    TraceContext context;

    @Override
    public boolean onThisEntry(CharSequence tracestate, int beginIndex, int endIndex) {
      TraceContextOrSamplingFlags extracted = parseB3SingleFormat(tracestate, beginIndex, endIndex);
      if (extracted != null) context = extracted.context();
      return context != null;
    }
  }

  /** When present, this context was created with TracestatePropagation */
  static final Extra MARKER = new Extra();

  static final List<Object> DEFAULT_EXTRA = Collections.singletonList(MARKER);
  static final TraceContextOrSamplingFlags EMPTY =
    TraceContextOrSamplingFlags.EMPTY.toBuilder().extra(DEFAULT_EXTRA).build();
}
