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
import brave.propagation.TraceContext;

/**
 * @param <Chan> the type of the channel
 * @param <Msg> the type of the message
 * @param <C> the type that carriers the trace context, usually headers
 */
public final class ProducerHandler<Chan, Msg, C> {

  public static <Chan, Msg, C> ProducerHandler<Chan, Msg, C> create(
    MessagingTracing messagingTracing,
    MessagingAdapter<Chan, Msg, C> adapter,
    TraceContext.Extractor<C> extractor,
    TraceContext.Injector<C> injector
  ) {
    return new ProducerHandler<>(
      messagingTracing.tracing, messagingTracing.parser(), adapter, extractor, injector);
  }

  final Tracing tracing;
  final TraceContext.Extractor<C> extractor;
  final TraceContext.Injector<C> injector;
  final MessagingParser parser;
  final MessagingAdapter<Chan, Msg, C> adapter;

  ProducerHandler(Tracing tracing, MessagingParser parser,
    MessagingAdapter<Chan, Msg, C> adapter, TraceContext.Extractor<C> extractor,
    TraceContext.Injector<C> injector) {
    this.tracing = tracing;
    this.extractor = extractor;
    this.injector = injector;
    this.parser = parser;
    this.adapter = adapter;
  }

  /**
   * Attempts to resume a trace from the current span, falling back to extracting context from the
   * carrier. Tags are added before the span is started.
   *
   * <p>This is typically called before the send is processed by the actual library.
   */
  public Span startSend(Chan channel, Msg message) {
    C carrier = adapter.carrier(message);
    TraceContext maybeParent = tracing.currentTraceContext().get();
    // Unlike message consumers, we try current span before trying extraction. This is the proper
    // order because the span in scope should take precedence over a potentially stale header entry.
    //
    // NOTE: Brave instrumentation used properly does not result in stale header entries, as we use
    // propagation formats that can always be overwritten.
    Span span;
    if (maybeParent == null) {
      span = tracing.tracer().nextSpan(extractor.extract(carrier));
    } else {
      span = tracing.tracer().newChild(maybeParent);
    }

    if (!span.isNoop()) {
      span.kind(Span.Kind.PRODUCER);
      parser.start("send", adapter, channel, message, span.context(), span.customizer());
      String remoteServiceName = adapter.brokerName(channel);
      if (remoteServiceName != null) span.remoteServiceName(remoteServiceName);
      span.start();
    }

    injector.inject(span.context(), carrier);

    return span;
  }

  public void finishSend(Chan channel, Msg message, Span span) {
    parser.finish("send", adapter, channel, message, span.context(), span.customizer());
    span.finish();
  }
}
