/*
 * Copyright 2013-2024 The OpenZipkin Authors
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
package brave.rocketmq.client;

import brave.Span;
import brave.Tracer;
import brave.messaging.MessagingRequest;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.sampler.SamplerFunction;
import java.util.Map;

class Util {
  static <T extends MessagingRequest> Span createAndStartSpan(RocketMQTracing tracing,
    TraceContext.Extractor<T> extractor, SamplerFunction<MessagingRequest> sampler, T request,
    Map<String, String> props) {
    Tracer tracer = tracing.tracer;
    CurrentTraceContext currentTraceContext = tracing.tracing.currentTraceContext();
    TraceContext traceContext = currentTraceContext.get();
    Span span;

    if (traceContext == null) {
      TraceContextOrSamplingFlags extracted =
        tracing.extractAndClearTraceIdHeaders(extractor, request, props);
      span = tracing.nextMessagingSpan(sampler, request, extracted);
    } else {
      span = tracer.newChild(traceContext);
    }

    span.kind(request.spanKind());
    span.remoteServiceName(tracing.remoteServiceName);
    span.tag(RocketMQTags.ROCKETMQ_TOPIC, request.channelName());
    long timestamp = tracing.tracing.clock(span.context()).currentTimeMicroseconds();
    span.start(timestamp);
    return span;
  }

}
