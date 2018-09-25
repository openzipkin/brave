package brave;

import brave.handler.FinishedSpanHandler;
import brave.handler.MutableSpan;
import brave.internal.recorder.PendingSpans;
import brave.propagation.TraceContext;

/** This wraps the public api and guards access to a mutable span. */
final class RealSpan extends Span {

  final TraceContext context;
  final PendingSpans pendingSpans;
  final MutableSpan state;
  final Clock clock;
  final FinishedSpanHandler finishedSpanHandler;
  final RealSpanCustomizer customizer;

  RealSpan(TraceContext context,
      PendingSpans pendingSpans,
      MutableSpan state,
      Clock clock,
      FinishedSpanHandler finishedSpanHandler
  ) {
    this.context = context;
    this.pendingSpans = pendingSpans;
    this.state = state;
    this.clock = clock;
    this.customizer = new RealSpanCustomizer(context, state, clock);
    this.finishedSpanHandler = finishedSpanHandler;
  }

  @Override public boolean isNoop() {
    return false;
  }

  @Override public TraceContext context() {
    return context;
  }

  @Override public SpanCustomizer customizer() {
    return new RealSpanCustomizer(context, state, clock);
  }

  @Override public Span start() {
    return start(clock.currentTimeMicroseconds());
  }

  @Override public Span start(long timestamp) {
    synchronized (state) {
      state.startTimestamp(timestamp);
    }
    return this;
  }

  @Override public Span name(String name) {
    synchronized (state) {
      state.name(name);
    }
    return this;
  }

  @Override public Span kind(Kind kind) {
    synchronized (state) {
      state.kind(kind);
    }
    return this;
  }

  @Override public Span annotate(String value) {
    return annotate(clock.currentTimeMicroseconds(), value);
  }

  @Override public Span annotate(long timestamp, String value) {
    // Modern instrumentation should not send annotations such as this, but we leniently
    // accept them rather than fail. This for example allows old bridges like to Brave v3 to work
    if ("cs".equals(value)) {
      synchronized (state) {
        state.kind(Span.Kind.CLIENT);
        state.startTimestamp(timestamp);
      }
    } else if ("sr".equals(value)) {
      synchronized (state) {
        state.kind(Span.Kind.SERVER);
        state.startTimestamp(timestamp);
      }
    } else if ("cr".equals(value)) {
      synchronized (state) {
        state.kind(Span.Kind.CLIENT);
      }
      finish(timestamp);
    } else if ("ss".equals(value)) {
      synchronized (state) {
        state.kind(Span.Kind.SERVER);
      }
      finish(timestamp);
    } else {
      synchronized (state) {
        state.annotate(timestamp, value);
      }
    }
    return this;
  }

  @Override public Span tag(String key, String value) {
    synchronized (state) {
      state.tag(key, value);
    }
    return this;
  }

  @Override public Span error(Throwable throwable) {
    synchronized (state) {
      state.error(throwable);
    }
    return this;
  }

  @Override public Span remoteServiceName(String remoteServiceName) {
    synchronized (state) {
      state.remoteServiceName(remoteServiceName);
    }
    return this;
  }

  @Override public boolean remoteIpAndPort(String remoteIp, int remotePort) {
    synchronized (state) {
      return state.remoteIpAndPort(remoteIp, remotePort);
    }
  }

  @Override public void finish() {
    finish(clock.currentTimeMicroseconds());
  }

  @Override public void finish(long timestamp) {
    if (!pendingSpans.remove(context)) return;
    synchronized (state) {
      state.finishTimestamp(timestamp);
    }
    finishedSpanHandler.handle(context, state);
  }

  @Override public void abandon() {
    pendingSpans.remove(context);
  }

  @Override public void flush() {
    abandon();
    finishedSpanHandler.handle(context, state);
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
