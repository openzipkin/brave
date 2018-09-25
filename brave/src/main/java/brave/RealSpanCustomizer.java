package brave;

import brave.handler.MutableSpan;
import brave.propagation.TraceContext;

/** This wraps the public api and guards access to a mutable span. */
final class RealSpanCustomizer implements SpanCustomizer {

  final TraceContext context;
  final MutableSpan state;
  final Clock clock;

  RealSpanCustomizer(TraceContext context, MutableSpan state, Clock clock) {
    this.context = context;
    this.state = state;
    this.clock = clock;
  }

  @Override public SpanCustomizer name(String name) {
    synchronized (state) {
      state.name(name);
    }
    return this;
  }

  @Override public SpanCustomizer annotate(String value) {
    long timestamp = clock.currentTimeMicroseconds();
    synchronized (state) {
      state.annotate(timestamp, value);
    }
    return this;
  }

  @Override public SpanCustomizer tag(String key, String value) {
    synchronized (state) {
      state.tag(key, value);
    }
    return this;
  }

  @Override
  public String toString() {
    return "RealSpanCustomizer(" + context + ")";
  }
}
