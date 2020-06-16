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
package brave.jms;

import brave.Span;
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import javax.jms.CompletionListener;
import javax.jms.Destination;
import javax.jms.Message;

/**
 * Decorates, then finishes a producer span. Allows tracing to record the duration between batching
 * for send and actual send.
 */
@JMS2_0 final class TracingCompletionListener implements CompletionListener {
  static CompletionListener create(CompletionListener delegate,
    @Nullable Destination destination, Span span, CurrentTraceContext current) {
    return new TracingCompletionListener(delegate, destination, span, current);
  }

  final CompletionListener delegate;
  final CurrentTraceContext current;
  @Nullable final Destination destination;
  final Span span;

  TracingCompletionListener(CompletionListener delegate, Destination destination, Span span,
    CurrentTraceContext current) {
    this.delegate = delegate;
    this.destination = destination;
    this.span = span;
    this.current = current;
  }

  @Override public void onCompletion(Message message) {
    try (Scope ws = current.maybeScope(span.context())) {
      delegate.onCompletion(message);
    } finally {
      // TODO: in order to tag messageId
      // parse(new MessageConsumerRequest(message, destination))
      span.finish();
    }
  }

  @Override public void onException(Message message, Exception exception) {
    try (Scope ws = current.maybeScope(span.context())) {
      // TODO: in order to tag messageId
      // parse(new MessageConsumerRequest(message, destination))
      delegate.onException(message, exception);
    } finally {
      span.error(exception);
      span.finish();
    }
  }
}
