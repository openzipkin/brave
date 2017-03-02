package brave;

import brave.propagation.TraceContext;
import brave.propagation.TraceContextHolder;
import zipkin.Endpoint;

final class NoopSpan extends Span {
  final TraceContext context;

  NoopSpan(TraceContext context) {
    this.context = context;
  }

  @Override public boolean isNoop() {
    return true;
  }

  @Override public TraceContext context() {
    return context;
  }

  @Override public Span start() {
    TraceContextHolder.push(context);
    return this;
  }

  @Override public Span start(long timestamp) {
    TraceContextHolder.push(context);
    return this;
  }

  @Override public Span name(String name) {
    return this;
  }

  @Override public Span kind(Kind kind) {
    return this;
  }

  @Override public Span annotate(String value) {
    return this;
  }

  @Override public Span annotate(long timestamp, String value) {
    return this;
  }

  @Override public Span remoteEndpoint(Endpoint endpoint) {
    return this;
  }

  @Override public Span tag(String key, String value) {
    return this;
  }

  @Override public void finish() {
    TraceContextHolder.pop();
  }

  @Override public void finish(long timestamp) {
    TraceContextHolder.pop();
  }

  @Override public void flush() {
  }

  @Override
  public String toString() {
    return "NoopSpan(" + context + ")";
  }
}
