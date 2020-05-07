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
import static brave.propagation.w3c.TraceContextPropagation.TRACEPARENT;
import static brave.propagation.w3c.TraceContextPropagation.TRACESTATE;

// TODO: this class has no useful tests wrt traceparent yet
final class TraceContextExtractor<R> implements Extractor<R> {
  final Getter<R, String> getter;
  final TraceContextPropagation propagation;

  TraceContextExtractor(TraceContextPropagation propagation, Getter<R, String> getter) {
    this.getter = getter;
    this.propagation = propagation;
  }

  @Override public TraceContextOrSamplingFlags extract(R request) {
    if (request == null) throw new NullPointerException("request == null");
    String traceparentString = getter.get(request, TRACEPARENT);
    if (traceparentString == null) return TraceContextOrSamplingFlags.EMPTY;

    // TODO: add link that says tracestate itself is optional
    String tracestateString = getter.get(request, TRACESTATE);
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
      return TraceContextOrSamplingFlags.create(maybeUpstream);
    }

    Tracestate tracestate = propagation.tracestateFactory.create();
    TraceContextOrSamplingFlags extracted = null;
    if (TracestateFormat.INSTANCE.parseInto(tracestateString, tracestate)) {
      String b3 = tracestate.get(propagation.tracestateKey);
      if (b3 != null) {
        tracestate.put(propagation.tracestateKey, null);
        extracted = parseB3SingleFormat(b3);
      }
    }
    if (extracted == null) extracted = TraceContextOrSamplingFlags.EMPTY;
    return extracted.toBuilder().addExtra(tracestate).build();
  }
}
