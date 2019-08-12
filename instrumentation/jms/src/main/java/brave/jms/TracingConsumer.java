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
import brave.Tracer;
import brave.Tracing;
import brave.internal.Nullable;
import brave.propagation.TraceContextOrSamplingFlags;
import javax.jms.Destination;
import javax.jms.Message;

abstract class TracingConsumer<C> {
  final C delegate;
  final JmsTracing jmsTracing;
  final Tracing tracing;
  final Tracer tracer;
  @Nullable final String remoteServiceName;

  TracingConsumer(C delegate, JmsTracing jmsTracing) {
    this.delegate = delegate;
    this.jmsTracing = jmsTracing;
    this.tracing = jmsTracing.tracing;
    this.tracer = tracing.tracer();
    this.remoteServiceName = jmsTracing.remoteServiceName;
  }

  void handleReceive(Message message) {
    if (message == null || tracing.isNoop()) return;
    // remove prior propagation headers from the message
    TraceContextOrSamplingFlags extracted = jmsTracing.extractAndClearMessage(message);
    Span span = tracer.nextSpan(extracted);
    if (!span.isNoop()) {
      span.name("receive").kind(Span.Kind.CONSUMER);
      Destination destination = destination(message);
      if (destination != null) jmsTracing.tagQueueOrTopic(destination, span);
      if (remoteServiceName != null) span.remoteServiceName(remoteServiceName);

      // incur timestamp overhead only once
      long timestamp = tracing.clock(span.context()).currentTimeMicroseconds();
      span.start(timestamp).finish(timestamp);
    }
    jmsTracing.setNextParent(message, span.context());
  }

  abstract @Nullable Destination destination(Message message);
}
