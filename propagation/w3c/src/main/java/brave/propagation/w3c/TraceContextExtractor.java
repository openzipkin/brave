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
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContextOrSamplingFlags;

import static brave.propagation.B3SingleFormat.parseB3SingleFormat;

// TODO: this class has no useful tests wrt traceparent yet
final class TraceContextExtractor<R, K> implements Extractor<R> {
  static final TraceContextOrSamplingFlags EXTRACTED_EMPTY =
      TraceContextOrSamplingFlags.EMPTY.toBuilder().addExtra(Tracestate.EMPTY).build();

  final Getter<R, K> getter;
  final K traceparentKey, tracestateKey;
  final TracestateFormat tracestateFormat;
  final B3SingleFormatHandler handler = new B3SingleFormatHandler();

  TraceContextExtractor(TraceContextPropagation<K> propagation, Getter<R, K> getter) {
    this.getter = getter;
    this.traceparentKey = propagation.traceparent;
    this.tracestateKey = propagation.tracestate;
    this.tracestateFormat = new TracestateFormat(propagation.tracestateKey);
  }

  @Override public TraceContextOrSamplingFlags extract(R request) {
    if (request == null) throw new NullPointerException("request == null");
    String traceparentString = getter.get(request, traceparentKey);
    if (traceparentString == null) return EXTRACTED_EMPTY;

    // TODO: add link that says tracestate itself is optional
    String tracestateString = getter.get(request, tracestateKey);
    if (tracestateString == null) {
      // NOTE: we may not want to pay attention to the sampled flag. Since it conflates
      // not-yet-sampled with sampled=false, implementations that always set flags to -00 would
      // never be traced!
      //
      // NOTE: We are required to use the same trace ID, there's some vagueness about the parent
      // span ID. Ex we don't know if upstream are sending to the same system or not, when we can't
      // read the tracestate header. Trusting the span ID (traceparent calls the span ID parent-id)
      // could result in a headless trace.
      TraceContext maybeUpstream = TraceparentFormat.parseTraceparentFormat(traceparentString);
      return TraceContextOrSamplingFlags.newBuilder(maybeUpstream)
          .addExtra(Tracestate.EMPTY) // marker for outbound propagation
          .build();
    }

    Tracestate tracestate = tracestateFormat.parseAndReturnOtherEntries(tracestateString, handler);

    TraceContext context = handler.context;
    if (context == null) {
      if (tracestate == Tracestate.EMPTY) return EXTRACTED_EMPTY;
      return EXTRACTED_EMPTY.toBuilder().addExtra(tracestate).build();
    }
    return TraceContextOrSamplingFlags.newBuilder(context).addExtra(tracestate).build();
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
}
