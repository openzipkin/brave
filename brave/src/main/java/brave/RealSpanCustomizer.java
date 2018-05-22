package brave;

import brave.internal.recorder.Recorder;
import brave.propagation.TraceContext;
import com.google.auto.value.AutoValue;

/** This wraps the public api and guards access to a mutable span. */
@AutoValue
abstract class RealSpanCustomizer implements SpanCustomizer {

  abstract TraceContext context();
  abstract Recorder recorder();

  static RealSpanCustomizer create(TraceContext context, Recorder recorder) {
    return new AutoValue_RealSpanCustomizer(context, recorder);
  }

  @Override public SpanCustomizer name(String name) {
    recorder().name(context(), name);
    return this;
  }

  @Override public SpanCustomizer annotate(String value) {
    recorder().annotate(context(), value);
    return this;
  }

  @Override public SpanCustomizer tag(String key, String value) {
    recorder().tag(context(), key, value);
    return this;
  }

  @Override
  public String toString() {
    return "RealSpanCustomizer(" + context() + ")";
  }
}
