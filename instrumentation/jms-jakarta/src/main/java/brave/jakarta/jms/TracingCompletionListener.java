/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jakarta.jms;

import brave.Span;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import jakarta.jms.CompletionListener;
import jakarta.jms.Message;

/**
 * Decorates, then finishes a producer span. Allows tracing to record the duration between batching
 * for send and actual send.
 */
final class TracingCompletionListener implements CompletionListener {
  static CompletionListener create(CompletionListener delegate, Span span,
    CurrentTraceContext current) {
    return new TracingCompletionListener(delegate, span, current);
  }

  final CompletionListener delegate;
  final CurrentTraceContext current;
  final Span span;

  TracingCompletionListener(CompletionListener delegate, Span span, CurrentTraceContext current) {
    this.delegate = delegate;
    this.span = span;
    this.current = current;
  }

  @Override public void onCompletion(Message message) {
    try (Scope scope = current.maybeScope(span.context())) {
      delegate.onCompletion(message);
    } finally {
      // TODO: in order to tag messageId
      // parse(new MessageConsumerRequest(message, destination))
      span.finish();
    }
  }

  @Override public void onException(Message message, Exception exception) {
    try (Scope scope = current.maybeScope(span.context())) {
      // TODO: in order to tag messageId
      // parse(new MessageConsumerRequest(message, destination))
      delegate.onException(message, exception);
    } finally {
      span.error(exception);
      span.finish();
    }
  }
}
