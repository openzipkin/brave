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
package brave.jms;

import brave.Span;
import brave.messaging.ProcessorHandler;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageListener;

import static brave.jms.JmsTracing.destination;

/**
 * When {@link #addConsumerSpan} this creates 2 spans:
 * <ol>
 *   <li>A duration 1 {@link Span.Kind#CONSUMER} span to represent receipt from the destination</li>
 *   <li>A child span with the duration of the delegated listener</li>
 * </ol>
 *
 * <p>{@link #addConsumerSpan} should only be set when the message consumer is not traced.
 */
final class TracingMessageListener implements MessageListener {
  /** Creates a message listener which also adds a consumer span. */
  static MessageListener create(MessageListener delegate, JmsTracing jmsTracing) {
    if (delegate instanceof TracingMessageListener) return delegate;
    return new TracingMessageListener(delegate, jmsTracing, true);
  }

  final MessageListener delegate;
  final CurrentTraceContext current;
  final ProcessorHandler<Destination, Message, Message> handler;
  final boolean addConsumerSpan;

  TracingMessageListener(MessageListener delegate, JmsTracing jmsTracing, boolean addConsumerSpan) {
    this.delegate = delegate;
    this.current = jmsTracing.messageTracing.tracing().currentTraceContext();
    this.handler = jmsTracing.processorHandler;
    this.addConsumerSpan = addConsumerSpan;
  }

  @Override public void onMessage(Message message) {
    Span listenerSpan =
      handler.startProcessor(destination(message), message, addConsumerSpan).name("on-message");
    try (Scope ws = current.newScope(listenerSpan.context())) {
      delegate.onMessage(message);
    } catch (RuntimeException | Error e) {
      listenerSpan.error(e);
      throw e;
    } finally {
      listenerSpan.finish();
    }
  }
}
