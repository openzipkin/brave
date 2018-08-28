package brave.jms;

import brave.Span;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import brave.internal.Nullable;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;

final class TracingExceptionListener {
  static ExceptionListener create(@Nullable ExceptionListener delegate, Tracer tracer) {
    if (delegate == null) return new TagError(tracer);
    if (delegate instanceof TagError) return delegate;
    return new DelegateAndTagError(delegate, tracer);
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
      try (SpanInScope ws = tracer.withSpanInScope(span)) {
        delegate.onException(exception);
      } finally {
        span.error(exception);
      }
    }
  }
}
