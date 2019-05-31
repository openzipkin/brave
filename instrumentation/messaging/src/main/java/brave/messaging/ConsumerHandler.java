/*
 * Copyright 2013-2019 The OpenZipkin Authors
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
package brave.messaging;

import brave.Span;
import brave.Tracing;
import brave.internal.Nullable;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.List;
import java.util.Map;

/**
 * @param <Chan> the type of the channel
 * @param <Msg> the type of the message
 * @param <C> the type that carriers the trace context, usually headers
 */
public final class ConsumerHandler<Chan, Msg, C> {

  public static <Chan, Msg, C> ConsumerHandler<Chan, Msg, C> create(
    MessagingTracing messagingTracing,
    MessagingAdapter<Chan, Msg, C> adapter,
    TraceContext.Extractor<C> extractor,
    TraceContext.Injector<C> injector
  ) {
    return new ConsumerHandler<>(messagingTracing, adapter, extractor, injector);
  }

  final Tracing tracing;
  final TraceContext.Extractor<C> extractor;
  final TraceContext.Injector<C> injector;
  final MessagingParser parser;
  final MessagingAdapter<Chan, Msg, C> adapter;

  ConsumerHandler(MessagingTracing messagingTracing,
    MessagingAdapter<Chan, Msg, C> adapter,
    TraceContext.Extractor<C> extractor,
    TraceContext.Injector<C> injector
  ) {
    this.tracing = messagingTracing.tracing;
    this.extractor = extractor;
    this.injector = injector;
    this.parser = messagingTracing.parser;
    this.adapter = adapter;
  }

  public void handleReceive(Chan channel, Msg message) {
    if (message == null || tracing.isNoop()) return;
    C carrier = adapter.carrier(message);
    TraceContextOrSamplingFlags extracted = extractor.extract(carrier);
    handleReceive(channel, message, carrier, adapter.brokerName(channel), extracted, false);
  }

  /** Returns a started processor span if {@code createProcessor} is true */
  @Nullable Span handleReceive(Chan channel, Msg message, C carrier, String remoteServiceName,
    TraceContextOrSamplingFlags extracted, boolean createProcessor) {
    Span span = tracing.tracer().nextSpan(extracted);
    // Creating the processor while the consumer is not finished ensures clocks are the same. This
    // allows the processor to start later, but not be subject to clock drift relative to the parent.
    Span processorSpan = createProcessor ? tracing.tracer().newChild(span.context()) : null;
    if (!span.isNoop()) {
      span.kind(Span.Kind.CONSUMER);
      parser.start("receive", adapter, channel, message, span.context(), span.customizer());
      if (remoteServiceName != null) span.remoteServiceName(remoteServiceName);

      // incur timestamp overhead only once
      long timestamp = tracing.clock(span.context()).currentTimeMicroseconds();
      span.start(timestamp);
      parser.finish("receive", adapter, channel, message, span.context(), span.customizer());
      span.finish(timestamp + 1);

      // eventhough we are setting the timestamp here, start timestamp is allowed to be overwritten
      // later as needed. Doing so here avoids the overhead of a tick reading
      if (processorSpan != null) processorSpan.start(timestamp + 1);
    }
    injector.inject(createProcessor ? processorSpan.context() : span.context(), carrier);
    return processorSpan;
  }

  public Map<Chan, Span> startBulkReceive(Chan channel, List<? extends Msg> messages,
    Map<Chan, Span> spanForChannel) {
    long timestamp = 0L;
    for (int i = 0, length = messages.size(); i < length; i++) {
      Msg message = messages.get(i);
      C carrier = adapter.carrier(message);
      TraceContextOrSamplingFlags extracted = extractor.extract(carrier);
      String remoteServiceName = adapter.brokerName(channel);

      // If we extracted neither a trace context, nor request-scoped data (extra),
      // make or reuse a span for this topic
      if (extracted.samplingFlags() != null && extracted.extra().isEmpty()) {
        Span span = spanForChannel.get(channel);
        if (span == null) {
          span = tracing.tracer().nextSpan(extracted);
          if (!span.isNoop()) {
            span.kind(Span.Kind.CONSUMER);
            parser.start("receive-batch", adapter, channel, null, span.context(),
              span.customizer());
            if (remoteServiceName != null) span.remoteServiceName(remoteServiceName);
            // incur timestamp overhead only once
            if (timestamp == 0L) {
              timestamp = tracing.clock(span.context()).currentTimeMicroseconds();
            }
            span.start(timestamp);
          }
          spanForChannel.put(channel, span);
        }
        injector.inject(span.context(), carrier);
      } else { // we extracted request-scoped data, so cannot share a consumer span.
        handleReceive(channel, message, carrier, remoteServiceName, extracted, false);
      }
    }
    return spanForChannel;
  }

  public void finishBulkReceive(Map<Chan, Span> spanForChannel) {
    long timestamp = 0L;
    for (Map.Entry<Chan, Span> entry : spanForChannel.entrySet()) {
      Span span = entry.getValue();
      // incur timestamp overhead only once
      if (timestamp == 0L) {
        timestamp = tracing.clock(span.context()).currentTimeMicroseconds();
      }
      parser.finish("receive-batch", adapter, entry.getKey(), null, span.context(),
        span.customizer());
      span.finish(timestamp);
    }
  }
}
