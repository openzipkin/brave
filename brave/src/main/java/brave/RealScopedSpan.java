package brave;

import brave.internal.recorder.Recorder;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;

/** This wraps the public api and guards access to a mutable span. */
final class RealScopedSpan extends ScopedSpan {

  final TraceContext context;
  final Scope scope;
  final Recorder recorder;
  final ErrorParser errorParser;

  RealScopedSpan(TraceContext context, Scope scope, Recorder recorder, ErrorParser errorParser) {
    this.context = context;
    this.scope = scope;
    this.recorder = recorder;
    this.errorParser = errorParser;
  }

  @Override public boolean isNoop() {
    return false;
  }

  @Override public TraceContext context() {
    return context;
  }

  @Override public ScopedSpan annotate(String value) {
    recorder.annotate(context, value);
    return this;
  }

  @Override public ScopedSpan tag(String key, String value) {
    recorder.tag(context, key, value);
    return this;
  }

  @Override public ScopedSpan error(Throwable throwable) {
    errorParser.error(throwable, this);
    return this;
  }

  @Override public void finish() {
    scope.close();
    recorder.finish(context);
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof RealScopedSpan)) return false;
    RealScopedSpan that = (RealScopedSpan) o;
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
