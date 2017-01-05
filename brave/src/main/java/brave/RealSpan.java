package brave;

import brave.internal.recorder.Recorder;
import brave.propagation.TraceContext;
import zipkin.Endpoint;

/** This wraps the public api and guards access to a mutable span. */
final class RealSpan extends Span {

  final TraceContext context;
  final Clock clock;
  final Recorder recorder;

  RealSpan(TraceContext context, Clock clock, Recorder recorder) {
    this.context = context;
    this.clock = clock;
    this.recorder = recorder;
  }

  @Override public boolean isNoop() {
    return false;
  }

  @Override public TraceContext context() {
    return context;
  }

  @Override public Span start() {
    return start(clock.currentTimeMicroseconds());
  }

  @Override public Span start(long timestamp) {
    recorder.start(context, timestamp);
    return this;
  }

  @Override public Span name(String name) {
    recorder.name(context, name);
    return this;
  }

  @Override public Span kind(Kind kind) {
    recorder.kind(context, kind);
    return this;
  }

  @Override public Span annotate(String value) {
    return annotate(clock.currentTimeMicroseconds(), value);
  }

  @Override public Span annotate(long timestamp, String value) {
    recorder.annotate(context, timestamp, value);
    return this;
  }

  @Override public Span tag(String key, String value) {
    recorder.tag(context, key, value);
    return this;
  }

  @Override public Span remoteEndpoint(Endpoint remoteEndpoint) {
    recorder.remoteEndpoint(context, remoteEndpoint);
    return this;
  }

  @Override public void finish() {
    finish(clock.currentTimeMicroseconds());
  }

  @Override public void finish(long timestamp) {
    recorder.finish(context, timestamp);
  }

  @Override public void flush() {
    recorder.flush(context);
  }

  @Override
  public String toString() {
    return "RealSpan(" + context + ")";
  }
}
