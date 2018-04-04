package brave;

import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;

final class NoopScopedSpan extends ScopedSpan {

  final TraceContext context;
  final Scope scope;

  NoopScopedSpan(TraceContext context, Scope scope) {
    this.context = context;
    this.scope = scope;
  }

  @Override public boolean isNoop() {
    return true;
  }

  @Override public TraceContext context() {
    return context;
  }

  @Override public ScopedSpan annotate(String value) {
    return this;
  }

  @Override public ScopedSpan tag(String key, String value) {
    return this;
  }

  @Override public ScopedSpan error(Throwable throwable) {
    return this;
  }

  @Override public void finish() {
    scope.close();
  }

  @Override public String toString() {
    return "NoopScopedSpan(" + context + ")";
  }
}
