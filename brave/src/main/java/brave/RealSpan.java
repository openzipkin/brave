package brave;

import brave.internal.recorder.Recorder;
import brave.propagation.TraceContext;
import zipkin2.Endpoint;

/** This wraps the public api and guards access to a mutable span. */
final class RealSpan extends Span {

  final TraceContext context;
  final Recorder recorder;
  final ErrorParser errorParser;
  final RealSpanCustomizer customizer;

  RealSpan(TraceContext context, Recorder recorder, ErrorParser errorParser) {
    this.context = context;
    this.recorder = recorder;
    this.customizer = new RealSpanCustomizer(context, recorder);
    this.errorParser = errorParser;
  }

  @Override public boolean isNoop() {
    return false;
  }

  @Override public TraceContext context() {
    return context;
  }

  @Override public SpanCustomizer customizer() {
    return new RealSpanCustomizer(context, recorder);
  }

  @Override public Span start() {
    recorder.start(context());
    return this;
  }

  @Override public Span start(long timestamp) {
    recorder.start(context(), timestamp);
    return this;
  }

  @Override public Span name(String name) {
    recorder.name(context(), name);
    return this;
  }

  @Override public Span kind(Kind kind) {
    recorder.kind(context(), kind);
    return this;
  }

  @Override public Span annotate(String value) {
    recorder.annotate(context(), value);
    return this;
  }

  @Override public Span annotate(long timestamp, String value) {
    recorder.annotate(context(), timestamp, value);
    return this;
  }

  @Override public Span tag(String key, String value) {
    recorder.tag(context(), key, value);
    return this;
  }

  @Override public Span error(Throwable throwable) {
    errorParser.error(throwable, customizer());
    return this;
  }

  @Override public Span remoteEndpoint(Endpoint remoteEndpoint) {
    recorder.remoteEndpoint(context(), remoteEndpoint);
    return this;
  }

  @Override public void finish() {
    recorder.finish(context());
  }

  @Override public void finish(long timestamp) {
    recorder.finish(context(), timestamp);
  }

  @Override public void abandon() {
    recorder.abandon(context());
  }

  @Override public void flush() {
    recorder.flush(context());
  }

  @Override public String toString() {
    return "RealSpan(" + context + ")";
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof RealSpan)) return false;
    return context.equals(((RealSpan) o).context);
  }

  @Override public int hashCode() {
    return context.hashCode();
  }
}
