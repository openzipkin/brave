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
    if (span.isNoop()) return delegate; // save allocation overhead
    return new TracingCompletionListener(delegate, span, current);
  }

  final Span span;
  final CompletionListener delegate;
  final CurrentTraceContext current;

  TracingCompletionListener(CompletionListener delegate, Span span, CurrentTraceContext current) {
    this.span = span;
    this.delegate = delegate;
    this.current = current;
  }

  @Override public void onCompletion(Message message) {
    try (Scope ws = current.maybeScope(span.context())) {
      delegate.onCompletion(message);
    } finally {
      span.finish();
    }
  }

  @Override public void onException(Message message, Exception exception) {
    try {
      delegate.onException(message, exception);
      span.error(exception);
    } finally {
      span.finish();
    }
  }
}
