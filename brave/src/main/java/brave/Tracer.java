package brave;

import brave.internal.Nullable;
import brave.internal.Platform;
import brave.internal.recorder.MutableSpan;
import brave.internal.recorder.PendingSpan;
import brave.internal.recorder.PendingSpans;
import brave.internal.recorder.SpanReporter;
import brave.propagation.CurrentTraceContext;
import brave.propagation.MutableTraceContext;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.sampler.DeclarativeSampler;
import brave.sampler.Sampler;
import java.io.Closeable;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Using a tracer, you can create a root span capturing the critical path of a request. Child spans
 * can be created to allocate latency relating to outgoing requests.
 *
 * When tracing single-threaded code, just run it inside a scoped span:
 * <pre>{@code
 * // Start a new trace or a span within an existing trace representing an operation
 * ScopedSpan span = tracer.startScopedSpan("encode");
 * try {
 *   // The span is in "scope" so that downstream code such as loggers can see trace IDs
 *   return encoder.encode();
 * } catch (RuntimeException | Error e) {
 *   span.error(e); // Unless you handle exceptions, you might not know the operation failed!
 *   throw e;
 * } finally {
 *   span.finish();
 * }
 * }</pre>
 *
 * <p>When you need more features, or finer control, use the {@linkplain Span} type:
 * <pre>{@code
 * // Start a new trace or a span within an existing trace representing an operation
 * Span span = tracer.nextSpan().name("encode").start();
 * // Put the span in "scope" so that downstream code such as loggers can see trace IDs
 * try (SpanInScope ws = tracer.withSpanInScope(span)) {
 *   return encoder.encode();
 * } catch (RuntimeException | Error e) {
 *   span.error(e); // Unless you handle exceptions, you might not know the operation failed!
 *   throw e;
 * } finally {
 *   span.finish(); // note the scope is independent of the span. Always finish a span.
 * }
 * }</pre>
 *
 * <p>Both of the above examples report the exact same span on finish!
 *
 * @see Span
 * @see ScopedSpan
 * @see Propagation
 */
public class Tracer {

  final Clock clock;
  final Propagation.Factory propagationFactory;
  final SpanReporter spanReporter; // for toString
  final PendingSpans pendingSpans;
  final Sampler sampler;
  final ErrorParser errorParser;
  final CurrentTraceContext currentTraceContext;
  final boolean traceId128Bit, supportsJoin;
  final AtomicBoolean noop;

  Tracer(
      Clock clock,
      Propagation.Factory propagationFactory,
      SpanReporter spanReporter,
      PendingSpans pendingSpans,
      Sampler sampler,
      ErrorParser errorParser,
      CurrentTraceContext currentTraceContext,
      boolean traceId128Bit,
      boolean supportsJoin,
      AtomicBoolean noop
  ) {
    this.clock = clock;
    this.propagationFactory = propagationFactory;
    this.spanReporter = spanReporter;
    this.pendingSpans = pendingSpans;
    this.sampler = sampler;
    this.errorParser = errorParser;
    this.currentTraceContext = currentTraceContext;
    this.traceId128Bit = traceId128Bit;
    this.supportsJoin = supportsJoin;
    this.noop = noop;
  }

  /**
   * Use this to temporarily override the sampler used when starting new traces. This also serves
   * advanced scenarios, such as {@link DeclarativeSampler declarative sampling}.
   *
   * <p>Simple example:
   * <pre>{@code
   * // Ensures new traces are always started
   * Tracer tracer = tracing.tracer().withSampler(Sampler.ALWAYS_SAMPLE);
   * }</pre>
   *
   * @see DeclarativeSampler
   */
  public Tracer withSampler(Sampler sampler) {
    if (sampler == null) throw new NullPointerException("sampler == null");
    return new Tracer(
        clock,
        propagationFactory,
        spanReporter,
        pendingSpans,
        sampler,
        errorParser,
        currentTraceContext,
        traceId128Bit,
        supportsJoin,
        noop
    );
  }

  /**
   * Explicitly creates a new trace. The result will be a root span (no parent span ID).
   *
   * <p>To implicitly create a new trace, or a span within an existing one, use {@link
   * #nextSpan()}.
   */
  public Span newTrace() {
    TraceContext context = nextContext(new MutableTraceContext(), false);
    return _toSpan(context);
  }

  /**
   * Joining is re-using the same trace and span ids extracted from an incoming RPC request. This
   * should not be used for messaging operations, as {@link #nextSpan(TraceContextOrSamplingFlags)}
   * is a better choice.
   *
   * <p>When this incoming context is sampled, we assume this is a shared span, one where the
   * caller and the current tracer report to the same span IDs. If no sampling decision occurred
   * yet, we have exclusive access to this span ID.
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
  public final Span joinSpan(TraceContext extracted) {
    if (extracted == null) throw new NullPointerException("extracted == null");
    if (!supportsJoin) return newChild(extracted);

    if (extracted.sampled() == null || extracted.sampled()) {
      return _joinSpan(MutableTraceContext.create(extracted));
    }
    return toSpan(extracted);
  }

  public final Span joinSpan(MutableTraceContext extracted) {
    if (extracted == null) throw new NullPointerException("extracted == null");
    if (!supportsJoin || extracted.spanId() == 0L) return nextSpan(extracted);
    return _joinSpan(extracted);
  }

  Span _joinSpan(MutableTraceContext mutableContext) {
    // When sampled, we are recording data under the same IDs as the caller
    if (Boolean.TRUE.equals(mutableContext.sampled())) {
      mutableContext.shared(true);
    } else if (mutableContext.sampled() == null) {
      mutableContext.sampled(sampler.isSampled(mutableContext.traceId()));
    }
    propagationFactory.decorate(mutableContext);
    return _toSpan(mutableContext.toTraceContext());
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
    return nextSpan(MutableTraceContext.create(parent));
  }

  /** TODO */
  public Span nextSpan(MutableTraceContext mutableContext) {
    TraceContext context = nextContext(mutableContext, true);
    return _toSpan(context);
  }

  TraceContext nextContext(MutableTraceContext mutableContext, boolean readCurrentSpan) {
    if (mutableContext == null) throw new NullPointerException("mutableContext == null");
    long traceId = mutableContext.traceId(), nextId = nextId();
    if (traceId == 0L) {
      TraceContext implicitParent;
      // Reuse the IDs from the current span as the parent
      if (readCurrentSpan && (implicitParent = currentTraceContext.get()) != null) {
        mutableContext.parent(implicitParent);
      } else { // make a new trace ID
        mutableContext.traceIdHigh(traceId128Bit ? Platform.get().nextTraceIdHigh() : 0L);
        mutableContext.traceId(nextId);
        mutableContext.parentId(0L); // ensure the parent ID is cleared.
      }
    } else { // try to set the parent from the extracted state
      mutableContext.parentId(mutableContext.spanId());
    }
    mutableContext.spanId(nextId);
    if (mutableContext.sampled() == null) {
      mutableContext.sampled(sampler.isSampled(mutableContext.traceId()));
    }
    propagationFactory.decorate(mutableContext);
    return mutableContext.toTraceContext();
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
    return nextSpan(MutableTraceContext.create(extracted));
  }

  /**
   * Converts the context to a Span object after ensuring it is sampled and decorating it for
   * propagation.
   */
  public Span toSpan(TraceContext context) {
    if (context == null) throw new NullPointerException("context == null");
    // decorating here addresses join, new traces or children and ad-hoc trace contexts
    if (context.sampled() == null) {
      MutableTraceContext mutableContext = MutableTraceContext.create(context);
      mutableContext.sampled(sampler.isSampled(mutableContext.traceId()));
      propagationFactory.decorate(mutableContext);
      return _toSpan(mutableContext.toTraceContext());
    }
    return _toSpan(propagationFactory.decorate(context));
  }

  Span _toSpan(TraceContext context) {
    if (isNoop(context)) return new NoopSpan(context);
    // allocate a mutable span in case multiple threads call this method.. they'll use the same data
    PendingSpan pendingSpan = pendingSpans.getOrCreate(context);
    return new RealSpan(context,
        pendingSpans,
        pendingSpan.state(),
        pendingSpan.clock(),
        spanReporter,
        errorParser
    );
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
   * // note: try-with-resources closes the scope *before* the catch block
   * } catch (RuntimeException | Error e) {
   *   span.error(e);
   *   throw e;
   * } finally {
   *   span.finish();
   * }
   *
   * // An unrelated framework interceptor can now lookup the correct parent for outbound requests
   * Span parent = tracer.currentSpan()
   * Span span = tracer.nextSpan().name("outbound").start(); // parent is implicitly looked up
   * try (SpanInScope ws = tracer.withSpanInScope(span)) {
   *   return outboundRequest.invoke();
   * // note: try-with-resources closes the scope *before* the catch block
   * } catch (RuntimeException | Error e) {
   *   span.error(e);
   *   throw e;
   * } finally {
   *   span.finish();
   * }
   * }</pre>
   *
   * <p>When tracing in-process commands, prefer {@link #startScopedSpan(String)} which scopes by
   * default.
   *
   * <p>Note: While downstream code might affect the span, calling this method, and calling close
   * on the result have no effect on the input. For example, calling close on the result does not
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
   * as a method-local variable vs repeated calls.
   */
  public SpanCustomizer currentSpanCustomizer() {
    // note: we don't need to decorate the context for propagation as it is only used for toString
    TraceContext context = currentTraceContext.get();
    if (context == null || isNoop(context)) return NoopSpanCustomizer.INSTANCE;
    PendingSpan pendingSpan = pendingSpans.getOrCreate(context);
    return new RealSpanCustomizer(context, pendingSpan.state(), pendingSpan.clock());
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

  /**
   * Returns a new child span if there's a {@link #currentSpan()} or a new trace if there isn't.
   *
   * <p>Prefer {@link #startScopedSpan(String)} if you are tracing a synchronous function or code
   * block.
   */
  public Span nextSpan() {
    TraceContext context = nextContext(new MutableTraceContext(), true);
    return _toSpan(context);
  }

  /**
   * Returns a new child span if there's a {@link #currentSpan()} or a new trace if there isn't. The
   * result is the "current span" until {@link ScopedSpan#finish()} is called.
   *
   * Here's an example:
   * <pre>{@code
   * ScopedSpan span = tracer.startScopedSpan("encode");
   * try {
   *   // The span is in "scope" so that downstream code such as loggers can see trace IDs
   *   return encoder.encode();
   * } catch (RuntimeException | Error e) {
   *   span.error(e); // Unless you handle exceptions, you might not know the operation failed!
   *   throw e;
   * } finally {
   *   span.finish();
   * }
   * }</pre>
   */
  public ScopedSpan startScopedSpan(String name) {
    return startScopedSpanWithParent(name, currentTraceContext.get());
  }

  /**
   * Same as {@link #startScopedSpan(String)}, except ignores the current trace context.
   *
   * <p>Use this when you are creating a scoped span in a method block where the parent was
   * created. You can also use this to force a new trace by passing null parent.
   */
  // this api is needed to make tools such as executors which need to carry the invocation context
  public ScopedSpan startScopedSpanWithParent(String name, @Nullable TraceContext parent) {
    if (name == null) throw new NullPointerException("name == null");
    MutableTraceContext mutableContext = new MutableTraceContext();
    if (parent != null) mutableContext.parent(parent);
    TraceContext context = nextContext(mutableContext, true);
    CurrentTraceContext.Scope scope = currentTraceContext.newScope(context);
    if (isNoop(context)) return new NoopScopedSpan(context, scope);
    PendingSpan pendingSpan = pendingSpans.getOrCreate(context);
    Clock clock = pendingSpan.clock();
    MutableSpan state = pendingSpan.state();
    state.name(name);
    state.startTimestamp(clock.currentTimeMicroseconds());
    return new RealScopedSpan(context, scope, state, clock, pendingSpans, spanReporter,
        errorParser);
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
    List<zipkin2.Span> inFlight = pendingSpans.snapshot();
    return "Tracer{"
        + (currentSpan != null ? ("currentSpan=" + currentSpan + ", ") : "")
        + (inFlight.size() > 0 ? ("inFlight=" + inFlight + ", ") : "")
        + (noop.get() ? "noop=true, " : "")
        + "reporter=" + spanReporter
        + "}";
  }

  boolean isNoop(TraceContext context) {
    return noop.get() || !Boolean.TRUE.equals(context.sampled());
  }

  /** Generates a new 64-bit ID, taking care to dodge zero which can be confused with absent */
  long nextId() {
    long nextId = Platform.get().randomLong();
    while (nextId == 0L) {
      nextId = Platform.get().randomLong();
    }
    return nextId;
  }
}
