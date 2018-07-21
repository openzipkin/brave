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

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof NoopScopedSpan)) return false;
    NoopScopedSpan that = (NoopScopedSpan) o;
    return context.equals(that.context) && scope.equals(that.scope);
  }

  @Override public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= context.hashCode();
    h *= 1000003;
    h ^= scope.hashCode();
    return h;
  }
}
