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
import brave.internal.Nullable;
import brave.messaging.MessagingRequest;
import brave.messaging.ProducerRequest;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.sampler.SamplerFunction;

abstract class TracingProducer<R extends ProducerRequest> {
  final JmsTracing jmsTracing;
  final Tracer tracer;
  final CurrentTraceContext current;
  final Extractor<R> extractor;
  final Injector<R> injector;
  final SamplerFunction<MessagingRequest> sampler;
  @Nullable final String remoteServiceName;

  TracingProducer(Extractor<R> extractor, Injector<R> injector, JmsTracing jmsTracing) {
    this.jmsTracing = jmsTracing;
    this.tracer = jmsTracing.tracing.tracer();
    this.current = jmsTracing.tracing.currentTraceContext();
    this.extractor = extractor;
    this.injector = injector;
    this.sampler = jmsTracing.producerSampler;
    this.remoteServiceName = jmsTracing.remoteServiceName;
  }

  Span createAndStartProducerSpan(R request) {
    TraceContext maybeParent = current.get();
    // Unlike message consumers, we try current span before trying extraction. This is the proper
    // order because the span in scope should take precedence over a potentially stale header entry.
    //
    // NOTE: The JMS Spec says you need to clear headers only when receiving a message, not when
    // sending one. At any rate, as long as we are using b3-single format, this is an overwrite not
    // a clear.
    Span span;
    TraceContextOrSamplingFlags extracted = null;
    if (maybeParent == null) {
      extracted = extractor.extract(request);
      span = jmsTracing.nextMessagingSpan(sampler, request, extracted);
    } else {
      span = tracer.newChild(maybeParent);
    }

    if (!span.isNoop()) {
      span.kind(Span.Kind.PRODUCER).name("send");
      jmsTracing.tagQueueOrTopic(request, span);
      if (remoteServiceName != null) span.remoteServiceName(remoteServiceName);
      span.start();
    }

    injector.inject(span.context(), request);
    return span;
  }
}
