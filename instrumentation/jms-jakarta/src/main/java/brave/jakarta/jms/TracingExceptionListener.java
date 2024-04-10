/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jakarta.jms;

import brave.Span;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import jakarta.jms.ExceptionListener;
import jakarta.jms.JMSException;

final class TracingExceptionListener {
  static ExceptionListener create(JmsTracing jmsTracing) {
    return new TagError(jmsTracing.tracing.tracer());
  }

  static ExceptionListener create(ExceptionListener delegate, JmsTracing jmsTracing) {
    if (delegate == null) throw new NullPointerException("exceptionListener == null");
    if (delegate instanceof TagError) return delegate;
    return new DelegateAndTagError(delegate, jmsTracing.tracing.tracer());
  }

  static class TagError implements ExceptionListener {
    final Tracer tracer;

    TagError(Tracer tracer) {
      this.tracer = tracer;
    }

    @Override public void onException(JMSException exception) {
      Span span = tracer.currentSpan();
      if (span != null) span.error(exception);
    }
  }

  static final class DelegateAndTagError extends TagError {
    final ExceptionListener delegate;

    DelegateAndTagError(ExceptionListener delegate, Tracer tracer) {
      super(tracer);
      this.delegate = delegate;
    }

    @Override public void onException(JMSException exception) {
      Span span = tracer.currentSpan();
      if (span == null) {
        delegate.onException(exception);
        return;
      }
      try (SpanInScope scope = tracer.withSpanInScope(span)) {
        delegate.onException(exception);
      } finally {
        span.error(exception);
      }
    }
  }
}
