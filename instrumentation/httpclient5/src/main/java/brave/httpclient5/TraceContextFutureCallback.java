package brave.httpclient5;

import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import org.apache.hc.core5.concurrent.FutureCallback;

final class TraceContextFutureCallback<T> implements FutureCallback<T> {
  final FutureCallback<T> delegate;
  final CurrentTraceContext currentTraceContext;
  final TraceContext invocationContext;

  TraceContextFutureCallback(FutureCallback<T> delegate,
    CurrentTraceContext currentTraceContext, TraceContext invocationContext) {
    this.delegate = delegate;
    this.currentTraceContext = currentTraceContext;
    this.invocationContext = invocationContext;
  }

  @Override
  public void completed(T t) {
    try (Scope scope = currentTraceContext.maybeScope(invocationContext)) {
      delegate.completed(t);
    }
  }

  @Override
  public void failed(Exception e) {
    try (Scope scope = currentTraceContext.maybeScope(invocationContext)) {
      delegate.failed(e);
    }
  }

  @Override
  public void cancelled() {
    try (Scope scope = currentTraceContext.maybeScope(invocationContext)) {
      delegate.cancelled();
    }
  }

  @Override
  public String toString() {
    return delegate.toString();
  }
}
