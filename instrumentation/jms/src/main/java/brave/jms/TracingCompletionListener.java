/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jms;

import brave.Span;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import javax.jms.CompletionListener;
import javax.jms.Message;

/**
 * Decorates, then finishes a producer span. Allows tracing to record the duration between batching
 * for send and actual send.
 */
@JMS2_0 final class TracingCompletionListener implements CompletionListener {
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
    Scope scope = current.maybeScope(span.context());
    try {
      delegate.onCompletion(message);
    } finally {
      // TODO: in order to tag messageId
      // parse(new MessageConsumerRequest(message, destination))
      span.finish();
      scope.close();
    }
  }

  @Override public void onException(Message message, Exception exception) {
    Scope scope = current.maybeScope(span.context());
    try {
      // TODO: in order to tag messageId
      // parse(new MessageConsumerRequest(message, destination))
      delegate.onException(message, exception);
    } finally {
      span.error(exception);
      span.finish();
      scope.close();
    }
  }
}
