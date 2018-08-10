package brave;

import brave.internal.Nullable;
import brave.internal.Platform;
import brave.internal.recorder.MutableSpan;
import brave.internal.recorder.PendingSpan;
import brave.internal.recorder.PendingSpans;
import brave.propagation.CurrentTraceContext;
import brave.propagation.MutableTraceContext;
import brave.propagation.Propagation;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.propagation.TraceIdContext;
import brave.sampler.DeclarativeSampler;
import brave.sampler.Sampler;
import java.io.Closeable;
import java.util.ArrayList;
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
  final Tracing.SpanReporter spanReporter; // for toString
  final PendingSpans pendingSpans;
  final Sampler sampler;
  final ErrorParser errorParser;
  final CurrentTraceContext currentTraceContext;
  final boolean traceId128Bit, supportsJoin;
  final AtomicBoolean noop;

  Tracer(
      Clock clock,
      Propagation.Factory propagationFactory,
      Tracing.SpanReporter spanReporter,
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
    return toSpan(newContextBuilder(null, sampler).build());
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
      context = context.toBuilder().shared(true).build();
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
    return toSpan(newContextBuilder(parent, sampler).build());
  }

  /** TODO */
  public Span nextSpan(MutableTraceContext extracted) {
    long traceId = extracted.traceId(), nextId = nextId();
    if (traceId == 0L) {
      TraceContext implicitParent = currentTraceContext.get();
      if (implicitParent != null) { // Reuse the IDs from the current span as the parent
        extracted.traceIdHigh(implicitParent.traceIdHigh());
        extracted.traceId(implicitParent.traceId());
        extracted.parentId(implicitParent.spanId());
        // fall through, with an implicit parent, not an extracted one
        for (int i = 0, length = implicitParent.extra().size(); i < length; i++) {
          extracted.addExtra(implicitParent.extra().get(i));
        }
      } else { // make a new trace ID
        extracted.traceIdHigh(traceId128Bit ? Platform.get().nextTraceIdHigh() : 0L);
        extracted.traceId(nextId);
        extracted.parentId(0L); // ensure the parent ID is cleared.
      }
    } else { // try to set the parent from the extracted state
      extracted.parentId(extracted.spanId());
    }
    extracted.spanId(nextId);
    if (extracted.sampled() == null) extracted.sampled(sampler.isSampled(extracted.traceId()));
    return toSpan(TraceContext.create(extracted));
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
        return toSpan(newContextBuilder(null, extracted.samplingFlags())
            .extra(extracted.extra()).build());
      }
      // fall through, with an implicit parent, not an extracted one
      parent = appendExtra(implicitParent, extracted.extra());
    }
    if (parent != null) {
      TraceContext.Builder builder;
      if (extracted.samplingFlags() != null) {
        builder = newContextBuilder(parent, extracted.samplingFlags());
      } else {
        builder = newContextBuilder(parent, sampler);
      }
      return toSpan(builder.shared(false).build());
    }
    TraceIdContext traceIdContext = extracted.traceIdContext();
    if (extracted.traceIdContext() != null) {
      Boolean sampled = traceIdContext.sampled();
      if (sampled == null) sampled = sampler.isSampled(traceIdContext.traceId());
      return toSpan(TraceContext.newBuilder()
          .sampled(sampled)
          .debug(traceIdContext.debug())
          .traceIdHigh(traceIdContext.traceIdHigh()).traceId(traceIdContext.traceId())
          .spanId(nextId())
          .extra(extracted.extra()).build());
    }
    // TraceContextOrSamplingFlags is a union of 3 types, we've checked all three
    throw new AssertionError("should not reach here");
  }

  /** Converts the context to a Span object after decorating it for propagation */
  public Span toSpan(TraceContext context) {
    if (context == null) throw new NullPointerException("context == null");
    // decorating here addresses join, new traces or children and ad-hoc trace contexts
    TraceContext decorated = propagationFactory.decorate(context);
    if (isNoop(decorated)) return new NoopSpan(decorated);
    // allocate a mutable span in case multiple threads call this method.. they'll use the same data
    PendingSpan pendingSpan = pendingSpans.getOrCreate(decorated);
    return new RealSpan(decorated,
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
    return toSpan(newContextBuilder(currentTraceContext.get(), sampler).build());
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
   * <p>Use this when you are creating a scoped span in a method block where the parent was created.
   * You can also use this to force a new trace by passing null parent.
   */
  // this api is needed to make tools such as executors which need to carry the invocation context
  public ScopedSpan startScopedSpanWithParent(String name, @Nullable TraceContext parent) {
    if (name == null) throw new NullPointerException("name == null");
    TraceContext context = propagationFactory.decorate(newContextBuilder(parent, sampler).build());
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

  TraceContext.Builder newContextBuilder(@Nullable TraceContext parent, Sampler sampler) {
    long nextId = nextId();
    if (parent != null) {
      Boolean sampled = parent.sampled();
      if (sampled == null) sampled = sampler.isSampled(parent.traceId());
      return parent.toBuilder() // copies "extra" from the parent
          .spanId(nextId)
          .parentId(parent.spanId())
          .sampled(sampled);
    }
    return TraceContext.newBuilder()
        .sampled(sampler.isSampled(nextId))
        .traceIdHigh(traceId128Bit ? Platform.get().nextTraceIdHigh() : 0L).traceId(nextId)
        .spanId(nextId);
  }

  boolean isNoop(TraceContext context) {
    return noop.get() || !Boolean.TRUE.equals(context.sampled());
  }

  TraceContext.Builder newContextBuilder(TraceContext parent, SamplingFlags samplingFlags) {
    return newContextBuilder(parent, samplerOverride(samplingFlags)).debug(samplingFlags.debug());
  }

  Sampler samplerOverride(SamplingFlags samplingFlags) {
    Boolean sampled = samplingFlags.sampled();
    return sampled == null ? sampler : sampled ? Sampler.ALWAYS_SAMPLE : Sampler.NEVER_SAMPLE;
  }

  /** Generates a new 64-bit ID, taking care to dodge zero which can be confused with absent */
  long nextId() {
    long nextId = Platform.get().randomLong();
    while (nextId == 0L) {
      nextId = Platform.get().randomLong();
    }
    return nextId;
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
