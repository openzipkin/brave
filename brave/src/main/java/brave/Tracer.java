package brave;

import brave.internal.Platform;
import brave.internal.recorder.Recorder;
import brave.propagation.CurrentTraceContext;
import brave.propagation.Propagation;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.sampler.Sampler;
import java.io.Closeable;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import zipkin.Endpoint;
import zipkin.reporter.Reporter;

/**
 * Using a tracer, you can create a root span capturing the critical path of a request. Child spans
 * can be created to allocate latency relating to outgoing requests.
 *
 * Here's a contrived example:
 * <pre>{@code
 * Span twoPhase = tracer.newTrace().name("twoPhase").start();
 * try {
 *   Span prepare = tracer.newChild(twoPhase.context()).name("prepare").start();
 *   try {
 *     prepare();
 *   } finally {
 *     prepare.finish();
 *   }
 *   Span commit = tracer.newChild(twoPhase.context()).name("commit").start();
 *   try {
 *     commit();
 *   } finally {
 *     commit.finish();
 *   }
 * } finally {
 *   twoPhase.finish();
 * }
 * }</pre>
 *
 * @see Span
 * @see Propagation
 */
public final class Tracer {

  /** @deprecated Please use {@link Tracing#newBuilder()} */
  @Deprecated public static Builder newBuilder() {
    return new Builder();
  }

  /** @deprecated Please use {@link Tracing.Builder} */
  @Deprecated public static final class Builder {
    final Tracing.Builder delegate = new Tracing.Builder();

    /** @see Tracing.Builder#localServiceName(String) */
    public Builder localServiceName(String localServiceName) {
      delegate.localServiceName(localServiceName);
      return this;
    }

    /** @see Tracing.Builder#localEndpoint(Endpoint) */
    public Builder localEndpoint(Endpoint localEndpoint) {
      delegate.localEndpoint(localEndpoint);
      return this;
    }

    /** @see Tracing.Builder#reporter(Reporter) */
    public Builder reporter(Reporter<zipkin.Span> reporter) {
      delegate.reporter(reporter);
      return this;
    }

    /** @see Tracing.Builder#clock(Clock) */
    public Builder clock(Clock clock) {
      delegate.clock(clock);
      return this;
    }

    /** @see Tracing.Builder#sampler(Sampler) */
    public Builder sampler(Sampler sampler) {
      delegate.sampler(sampler);
      return this;
    }

    /** @see Tracing.Builder#currentTraceContext(CurrentTraceContext) */
    public Builder currentTraceContext(CurrentTraceContext currentTraceContext) {
      delegate.currentTraceContext(currentTraceContext);
      return this;
    }

    /** @see Tracing.Builder#traceId128Bit(boolean) */
    public Builder traceId128Bit(boolean traceId128Bit) {
      delegate.traceId128Bit(traceId128Bit);
      return this;
    }

    public Tracer build() {
      return delegate.build().tracer();
    }
  }

  final Clock clock;
  final Endpoint localEndpoint;
  final Recorder recorder;
  final Sampler sampler;
  final CurrentTraceContext currentTraceContext;
  final boolean traceId128Bit;
  final AtomicBoolean noop;

  Tracer(Tracing.Builder builder, AtomicBoolean noop) {
    this.noop = noop;
    this.clock = builder.clock;
    this.localEndpoint = builder.localEndpoint;
    this.recorder = new Recorder(localEndpoint, clock, builder.reporter, this.noop);
    this.sampler = builder.sampler;
    this.currentTraceContext = builder.currentTraceContext;
    this.traceId128Bit = builder.traceId128Bit;
  }

  /** @deprecated use {@link Tracing#clock()} */
  @Deprecated public Clock clock() {
    return clock;
  }

  /**
   * Creates a new trace. If there is an existing trace, use {@link #newChild(TraceContext)}
   * instead.
   */
  public Span newTrace() {
    return ensureSampled(nextContext(null, SamplingFlags.EMPTY));
  }

  /**
   * Joining is re-using the same trace and span ids extracted from an incoming request. Here, we
   * ensure a sampling decision has been made. If the span passed sampling, we assume this is a
   * shared span, one where the caller and the current tracer report to the same span IDs. If no
   * sampling decision occurred yet, we have exclusive access to this span ID.
   *
   * <p>Here's an example of conditionally joining a span, depending on if a trace context was
   * extracted from an incoming request.
   *
   * <pre>{@code
   * contextOrFlags = extractor.extract(request);
   * span = contextOrFlags.context() != null
   *          ? tracer.joinSpan(contextOrFlags.context())
   *          : tracer.newTrace(contextOrFlags.samplingFlags());
   * }</pre>
   *
   * @see Propagation
   * @see Extractor#extract(Object)
   * @see TraceContextOrSamplingFlags#context()
   */
  public final Span joinSpan(TraceContext context) {
    if (context == null) throw new NullPointerException("context == null");
    // If we are joining a trace, we are sharing IDs with the caller
    return ensureSampled(context.toBuilder().shared(true).build());
  }

  /**
   * Like {@link #newTrace()}, but supports parameterized sampling, for example limiting on
   * operation or url pattern.
   *
   * <p>For example, to sample all requests for a specific url:
   * <pre>{@code
   * Span newTrace(Request input) {
   *   SamplingFlags flags = SamplingFlags.NONE;
   *   if (input.url().startsWith("/experimental")) {
   *     flags = SamplingFlags.SAMPLED;
   *   } else if (input.url().startsWith("/static")) {
   *     flags = SamplingFlags.NOT_SAMPLED;
   *   }
   *   return tracer.newTrace(flags);
   * }
   * }</pre>
   */
  public Span newTrace(SamplingFlags samplingFlags) {
    return ensureSampled(nextContext(null, samplingFlags));
  }

  /** Converts the context as-is to a Span object */
  public Span toSpan(TraceContext context) {
    if (context == null) throw new NullPointerException("context == null");
    if (noop.get() == false && Boolean.TRUE.equals(context.sampled())) {
      return RealSpan.create(context, clock, recorder);
    }
    return NoopSpan.create(context);
  }

  /**
   * Creates a new span within an existing trace. If there is no existing trace, use {@link
   * #newTrace()} instead.
   */
  public Span newChild(TraceContext parent) {
    if (parent == null) throw new NullPointerException("parent == null");
    if (Boolean.FALSE.equals(parent.sampled())) {
      return NoopSpan.create(parent);
    }
    return ensureSampled(nextContext(parent, parent));
  }

  Span ensureSampled(TraceContext context) {
    // If the sampled flag was left unset, we need to make the decision here
    if (context.sampled() == null) {
      context = context.toBuilder()
          .sampled(sampler.isSampled(context.traceId()))
          .shared(false)
          .build();
    }
    return toSpan(context);
  }

  TraceContext nextContext(@Nullable TraceContext parent, SamplingFlags samplingFlags) {
    long nextId = Platform.get().randomLong();
    if (parent != null) {
      return parent.toBuilder().spanId(nextId).parentId(parent.spanId()).build();
    }
    return TraceContext.newBuilder()
        .sampled(samplingFlags.sampled())
        .debug(samplingFlags.debug())
        .traceIdHigh(traceId128Bit ? Platform.get().randomLong() : 0L)
        .traceId(nextId)
        .spanId(nextId).build();
  }

  /**
   * Makes the given span the "current span" and returns an object that exits that scope on close.
   * The span provided will be returned by {@link #currentSpan()} until the return value is closed.
   *
   * <p>The most convenient way to use this method is via the try-with-resources idiom.
   *
   * Ex.
   * <pre>{@code
   * // Assume a framework interceptor uses this method to set the inbound span as current
   * try (SpanInScope ws = tracer.withSpanInScope(span)) {
   *   return inboundRequest.invoke();
   * } finally {
   *   span.finish();
   * }
   *
   * // An unrelated framework interceptor can now lookup the correct parent for an outbound
   * request
   * Span parent = tracer.currentSpan()
   * Span span = tracer.nextSpan().name("outbound").start(); // parent is implicitly looked up
   * try (SpanInScope ws = tracer.withSpanInScope(span)) {
   *   return outboundRequest.invoke();
   * } finally {
   *   span.finish();
   * }
   * }</pre>
   *
   * <p>Note: While downstream code might affect the span, calling this method, and calling close on
   * the result have no effect on the input. For example, calling close on the result does not
   * finish the span. Not only is it safe to call close, you must call close to end the scope, or
   * risk leaking resources associated with the scope.
   *
   * @param span span to place into scope or null to clear the scope
   */
  public SpanInScope withSpanInScope(@Nullable Span span) {
    return new SpanInScope(currentTraceContext.newScope(span != null ? span.context() : null));
  }

  /** Returns the current span in scope or null if there isn't one. */
  @Nullable public Span currentSpan() {
    TraceContext currentContext = currentTraceContext.get();
    return currentContext != null ? toSpan(currentContext) : null;
  }

  /** Returns a new child span if there's a {@link #currentSpan()} or a new trace if there isn't. */
  public Span nextSpan() {
    TraceContext parent = currentTraceContext.get();
    return parent == null ? newTrace() : newChild(parent);
  }

  /** A span remains in the scope it was bound to until close is called. */
  public static final class SpanInScope implements Closeable {
    final CurrentTraceContext.Scope scope;

    // This type hides the SPI type and allows us to double-check the SPI didn't return null.
    SpanInScope(CurrentTraceContext.Scope scope) {
      if (scope == null) throw new NullPointerException("scope == null");
      this.scope = scope;
    }

    /** No exceptions are thrown when unbinding a span scope. */
    @Override public void close() {
      scope.close();
    }

    @Override public String toString() {
      return scope.toString();
    }
  }
}
