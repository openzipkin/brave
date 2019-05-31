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
import brave.Tracer;
import brave.internal.Nullable;
import brave.propagation.TraceContextOrSamplingFlags;

/**
 * @param <Chan> the type of the channel
 * @param <Msg> the type of the message
 * @param <C> the type that carriers the trace context, usually headers
 */
public final class ProcessorHandler<Chan, Msg, C> {
  public static <Chan, Msg, C> ProcessorHandler<Chan, Msg, C> create(
    MessagingTracing messagingTracing,
    ConsumerHandler<Chan, Msg, C> consumerHandler
  ) {
    return new ProcessorHandler<>(messagingTracing, consumerHandler);
  }

  final Tracer tracer;
  final ConsumerHandler<Chan, Msg, C> consumerHandler;
  final MessagingAdapter<Chan, Msg, C> adapter;

  ProcessorHandler(MessagingTracing messagingTracing,
    ConsumerHandler<Chan, Msg, C> consumerHandler) {
    this.tracer = messagingTracing.tracing.tracer();
    this.consumerHandler = consumerHandler;
    this.adapter = consumerHandler.adapter;
  }

  /**
   * When {@code addConsumerSpan} is true, this creates 2 spans:
   * <ol>
   * <li>A duration 1 {@link Span.Kind#CONSUMER} span to represent receipt from the
   * destination</li>
   * <li>A child span with the duration of the delegated listener</li>
   * </ol>
   *
   * <p>{@code addConsumerSpan} should only be set when the message consumer is not traced.
   */
  // channel is nullable as JMS could have an exception getting it from the message
  public Span startProcessor(@Nullable Chan channel, Msg message, boolean addConsumerSpan) {
    C carrier = adapter.carrier(message);
    TraceContextOrSamplingFlags extracted = consumerHandler.extractor.extract(carrier);
    if (!addConsumerSpan) {
      Span result = tracer.nextSpan(extracted).start();
      if (!result.isNoop() && extracted.equals(TraceContextOrSamplingFlags.EMPTY)) {
        consumerHandler.parser
          .addMessageTags(adapter, channel, message, result.context(), result.customizer());
      }
      return result;
    }
    return consumerHandler.handleReceive(channel, message, carrier,
      adapter.brokerName(channel), extracted, true);
  }
}
