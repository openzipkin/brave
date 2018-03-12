package brave;

import brave.internal.Nullable;
import brave.internal.Platform;
import brave.internal.recorder.Recorder;
import brave.propagation.CurrentTraceContext;
import brave.propagation.Propagation;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.propagation.TraceIdContext;
import brave.sampler.Sampler;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import zipkin2.Endpoint;
import zipkin2.reporter.Reporter;

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

    /** @deprecated use {@link #endpoint(Endpoint)}, possibly with {@link zipkin.Endpoint#toV2()} */
    @Deprecated
    public Builder localEndpoint(zipkin.Endpoint localEndpoint) {
      return endpoint(localEndpoint.toV2());
    }

    /** @deprecated use {@link #endpoint(Endpoint)} */
    @Deprecated
    public Builder localEndpoint(Endpoint localEndpoint) {
      delegate.endpoint(localEndpoint);
      return this;
    }

    /** @see Tracing.Builder#endpoint(Endpoint) */
    public Builder endpoint(Endpoint endpoint) {
      delegate.endpoint(endpoint);
      return this;
    }

    /** @deprecated use {@link #spanReporter(Reporter)} */
    @Deprecated
    public Builder reporter(zipkin.reporter.Reporter<zipkin.Span> reporter) {
      delegate.reporter(reporter);
      return this;
    }

    /** @see Tracing.Builder#spanReporter(Reporter) */
    public Builder spanReporter(Reporter<zipkin2.Span> reporter) {
      delegate.spanReporter(reporter);
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

    /** @see Tracing.Builder#supportsJoin(boolean) */
    public Builder supportsJoin(boolean supportsJoin) {
      delegate.supportsJoin(supportsJoin);
      return this;
    }

    public Tracer build() {
      return delegate.build().tracer();
    }
  }

  final Clock clock;
  final Propagation.Factory propagationFactory;
  final Reporter<zipkin2.Span> reporter; // for toString
  final Recorder recorder;
  final Sampler sampler;
  final CurrentTraceContext currentTraceContext;
  final boolean traceId128Bit, supportsJoin;
  final AtomicBoolean noop;

  Tracer(Tracing.Builder builder, Clock clock, AtomicBoolean noop) {
    this.noop = noop;
    this.propagationFactory = builder.propagationFactory;
    this.supportsJoin = builder.supportsJoin && propagationFactory.supportsJoin();
    this.clock = clock;
    this.reporter = builder.reporter;
    this.recorder = new Recorder(builder.endpoint, clock, builder.reporter, this.noop);
    this.sampler = builder.sampler;
    this.currentTraceContext = builder.currentTraceContext;
    this.traceId128Bit = builder.traceId128Bit || propagationFactory.requires128BitTraceId();
  }

  /** @deprecated use {@link Tracing#clock(TraceContext)} */
  @Deprecated public Clock clock() {
    return clock;
  }

  /**
   * Explicitly creates a new trace. The result will be a root span (no parent span ID).
   *
   * <p>To implicitly create a new trace, or a span within an existing one, use {@link
   * #nextSpan()}.
   */
  public Span newTrace() {
    return toSpan(newRootContext(SamplingFlags.EMPTY, Collections.emptyList()));
  }

  /**
   * Joining is re-using the same trace and span ids extracted from an incoming RPC request. This
   * should not be used for messaging operations, as {@link #nextSpan(TraceContextOrSamplingFlags)}
   * is a better choice.
   *
   * <p>When this incoming context is sampled, we assume this is a shared span, one where the caller
   * and the current tracer report to the same span IDs. If no sampling decision occurred yet, we
   * have exclusive access to this span ID.
   *
   * <p>Here's an example of conditionally joining a span, depending on if a trace context was
   * extracted from an incoming request.
   *
   * <pre>{@code
   * extracted = extractor.extract(request);
   * span = contextOrFlags.context() != null
   *          ? tracer.joinSpan(contextOrFlags.context())
   *          : tracer.nextSpan(extracted);
   * }</pre>
   *
   * <p><em>Note:</em> When {@link Propagation.Factory#supportsJoin()} is false, this will always
   * fork a new child via {@link #newChild(TraceContext)}.
   *
   * @see Propagation
   * @see Extractor#extract(Object)
   * @see TraceContextOrSamplingFlags#context()
   */
  public final Span joinSpan(TraceContext context) {
    if (context == null) throw new NullPointerException("context == null");
    if (!supportsJoin) return newChild(context);
    // If we are joining a trace, we are sharing IDs with the caller
    // If the sampled flag was left unset, we need to make the decision here
    if (context.sampled() == null) { // then the caller didn't contribute data
      context = context.toBuilder().sampled(sampler.isSampled(context.traceId())).build();
    } else if (context.sampled()) { // we are recording and contributing to the same span ID
      recorder.setShared(context);
    }
    return toSpan(context);
  }

  /**
   * Explicitly creates a child within an existing trace. The result will be have its parent ID set
   * to the input's span ID. If a sampling decision has not yet been made, one will happen here.
   *
   * <p>To implicitly create a new trace, or a span within an existing one, use {@link
   * #nextSpan()}.
   */
  public Span newChild(TraceContext parent) {
    if (parent == null) throw new NullPointerException("parent == null");
    return nextSpan(TraceContextOrSamplingFlags.create(parent));
  }

  /**
   * This creates a new span based on parameters extracted from an incoming request. This will
   * always result in a new span. If no trace identifiers were extracted, a span will be created
   * based on the implicit context in the same manner as {@link #nextSpan()}. If a sampling decision
   * has not yet been made, one will happen here.
   *
   * <p>Ex.
   * <pre>{@code
   * extracted = extractor.extract(request);
   * span = tracer.nextSpan(extracted);
   * }</pre>
   *
   * <p><em>Note:</em> Unlike {@link #joinSpan(TraceContext)}, this does not attempt to re-use
   * extracted span IDs. This means the extracted context (if any) is the parent of the span
   * returned.
   *
   * <p><em>Note:</em> If a context could be extracted from the input, that trace is resumed, not
   * whatever the {@link #currentSpan()} was. Make sure you re-apply {@link #withSpanInScope(Span)}
   * so that data is written to the correct trace.
   *
   * @see Propagation
   * @see Extractor#extract(Object)
   * @see TraceContextOrSamplingFlags
   */
  public Span nextSpan(TraceContextOrSamplingFlags extracted) {
    TraceContext parent = extracted.context();
    if (extracted.samplingFlags() != null) {
      TraceContext implicitParent = currentTraceContext.get();
      if (implicitParent == null) {
        return toSpan(newRootContext(extracted.samplingFlags(), extracted.extra()));
      }
      // fall through, with an implicit parent, not an extracted one
      parent = appendExtra(implicitParent, extracted.extra());
    }
    long nextId = nextId();
    if (parent != null) {
      Boolean sampled = parent.sampled();
      if (sampled == null) sampled = sampler.isSampled(parent.traceId());
      return toSpan(parent.toBuilder() // copies "extra" from the parent
          .spanId(nextId)
          .parentId(parent.spanId())
          .sampled(sampled)
          .build());
    }
    TraceIdContext traceIdContext = extracted.traceIdContext();
    if (extracted.traceIdContext() != null) {
      Boolean sampled = traceIdContext.sampled();
      if (sampled == null) sampled = sampler.isSampled(traceIdContext.traceId());
      return toSpan(TraceContext.newBuilder()
          .sampled(sampled)
          .debug(traceIdContext.debug())
          .traceIdHigh(traceIdContext.traceIdHigh()).traceId(traceIdContext.traceId())
          .spanId(nextId)
          .extra(extracted.extra()).build());
    }
    // TraceContextOrSamplingFlags is a union of 3 types, we've checked all three
    throw new AssertionError("should not reach here");
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
    return toSpan(newRootContext(samplingFlags, Collections.emptyList()));
  }

  /** Converts the context as-is to a Span object */
  public Span toSpan(TraceContext context) {
    if (context == null) throw new NullPointerException("context == null");
    TraceContext decorated = propagationFactory.decorate(context);
    if (!noop.get() && Boolean.TRUE.equals(decorated.sampled())) {
      return RealSpan.create(decorated, recorder);
    }
    return NoopSpan.create(decorated);
  }

  TraceContext newRootContext(SamplingFlags samplingFlags, List<Object> extra) {
    long nextId = nextId();
    Boolean sampled = samplingFlags.sampled();
    if (sampled == null) sampled = sampler.isSampled(nextId);
    return TraceContext.newBuilder()
        .sampled(sampled)
        .traceIdHigh(traceId128Bit ? Platform.get().nextTraceIdHigh() : 0L).traceId(nextId)
        .spanId(nextId)
        .debug(samplingFlags.debug())
        .extra(extra).build();
  }

  /** Generates a new 64-bit ID, taking care to dodge zero which can be confused with absent */
  long nextId() {
    long nextId = Platform.get().randomLong();
    while (nextId == 0L) {
      nextId = Platform.get().randomLong();
    }
    return nextId;
  }

  /**
   * Makes the given span the "current span" and returns an object that exits that scope on close.
   * Calls to {@link #currentSpan()} and {@link #currentSpanCustomizer()} will affect this span
   * until the return value is closed.
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
   * // An unrelated framework interceptor can now lookup the correct parent for outbound requests
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

  /**
   * Returns a customizer for current span in scope or noop if there isn't one.
   *
   * <p>Unlike {@link CurrentSpanCustomizer}, this represents a single span. Accordingly, this
   * reference should not be saved as a field. That said, it is more efficient to save this result
   * as a method-local variable vs repeated calls to {@link #currentSpanCustomizer()}.
   */
  public SpanCustomizer currentSpanCustomizer() {
    TraceContext currentContext = currentTraceContext.get();
    return currentContext != null
        ? RealSpanCustomizer.create(currentContext, recorder) : NoopSpanCustomizer.INSTANCE;
  }

  /**
   * Returns the current span in scope or null if there isn't one.
   *
   * <p>When entering user code, prefer {@link #currentSpanCustomizer()} as it is a stable type and
   * will never return null.
   */
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

  @Override public String toString() {
    TraceContext currentSpan = currentTraceContext.get();
    List<zipkin2.Span> inFlight = recorder.snapshot();
    return "Tracer{"
        + (currentSpan != null ? ("currentSpan=" + currentSpan + ", ") : "")
        + (inFlight.size() > 0 ? ("inFlight=" + inFlight + ", ") : "")
        + (noop.get() ? "noop=true, " : "")
        + "reporter=" + reporter
        + "}";
  }

  static TraceContext appendExtra(TraceContext context, List<Object> extra) {
    if (extra.isEmpty()) return context;
    if (context.extra().isEmpty()) {
      return context.toBuilder().extra(extra).build();
    } else {
      List<Object> merged = new ArrayList<>(context.extra());
      merged.addAll(extra);
      return context.toBuilder().extra(merged).build();
    }
  }
}
