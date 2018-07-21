package brave;

import brave.internal.recorder.Recorder;
import brave.propagation.TraceContext;

/** This wraps the public api and guards access to a mutable span. */
final class RealSpanCustomizer implements SpanCustomizer {

  final TraceContext context;
  final Recorder recorder;

  RealSpanCustomizer(TraceContext context, Recorder recorder) {
    this.context = context;
    this.recorder = recorder;
  }

  @Override public SpanCustomizer name(String name) {
    recorder.name(context, name);
    return this;
  }

  @Override public SpanCustomizer annotate(String value) {
    recorder.annotate(context, value);
    return this;
  }

  @Override public SpanCustomizer tag(String key, String value) {
    recorder.tag(context, key, value);
    return this;
  }

  @Override
  public String toString() {
    return "RealSpanCustomizer(" + context + ")";
  }
}
