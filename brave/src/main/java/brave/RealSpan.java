package brave;

import brave.internal.recorder.Recorder;
import brave.propagation.TraceContext;
import com.google.auto.value.AutoValue;
import zipkin2.Endpoint;

/** This wraps the public api and guards access to a mutable span. */
@AutoValue
abstract class RealSpan extends Span {

  abstract Recorder recorder();
  abstract ErrorParser errorParser();

  static RealSpan create(TraceContext context, Recorder recorder, ErrorParser errorParser) {
    return new AutoValue_RealSpan(context, RealSpanCustomizer.create(context, recorder), recorder,
        errorParser);
  }

  @Override public boolean isNoop() {
    return false;
  }

  @Override public Span start() {
    recorder().start(context());
    return this;
  }

  @Override public Span start(long timestamp) {
    recorder().start(context(), timestamp);
    return this;
  }

  @Override public Span name(String name) {
    recorder().name(context(), name);
    return this;
  }

  @Override public Span kind(Kind kind) {
    recorder().kind(context(), kind);
    return this;
  }

  @Override public Span annotate(String value) {
    recorder().annotate(context(), value);
    return this;
  }

  @Override public Span annotate(long timestamp, String value) {
    recorder().annotate(context(), timestamp, value);
    return this;
  }

  @Override public Span tag(String key, String value) {
    recorder().tag(context(), key, value);
    return this;
  }

  @Override public Span error(Throwable throwable) {
    errorParser().error(throwable, customizer());
    return this;
  }

  @Override public Span remoteEndpoint(Endpoint remoteEndpoint) {
    recorder().remoteEndpoint(context(), remoteEndpoint);
    return this;
  }

  @Override public void finish() {
    recorder().finish(context());
  }

  @Override public void finish(long timestamp) {
    recorder().finish(context(), timestamp);
  }

  @Override public void abandon() {
    recorder().abandon(context());
  }

  @Override public void flush() {
    recorder().flush(context());
  }

  @Override
  public String toString() {
    return "RealSpan(" + context() + ")";
  }
}
