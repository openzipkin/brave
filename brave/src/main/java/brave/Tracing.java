/*
 * Copyright 2013-2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave;

import brave.baggage.BaggageField;
import brave.handler.FinishedSpanHandler;
import brave.handler.MutableSpan;
import brave.internal.IpLiteral;
import brave.internal.Nullable;
import brave.internal.Platform;
import brave.internal.handler.NoopAwareFinishedSpanHandler;
import brave.internal.handler.ZipkinFinishedSpanHandler;
import brave.internal.recorder.PendingSpans;
import brave.propagation.B3Propagation;
import brave.propagation.CurrentTraceContext;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import brave.sampler.SamplerFunction;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Reporter;

/**
 * This provides utilities needed for trace instrumentation. For example, a {@link Tracer}.
 *
 * <p>Instances built via {@link #newBuilder()} are registered automatically such that statically
 * configured instrumentation like JDBC drivers can use {@link #current()}.
 *
 * <p>This type can be extended so that the object graph can be built differently or overridden,
 * for example via spring or when mocking.
 */
public abstract class Tracing implements Closeable {
  static final AtomicReference<Tracing> CURRENT = new AtomicReference<>();

  public static Builder newBuilder() {
    return new Builder();
  }

  /** All tracing commands start with a {@link Span}. Use a tracer to create spans. */
  abstract public Tracer tracer();

  /**
   * When a trace leaves the process, it needs to be propagated, usually via headers. This utility
   * is used to inject or extract a trace context from remote requests.
   */
  // Implementations should override and cache this as a field.
  public Propagation<String> propagation() {
    return propagationFactory().create(Propagation.KeyFactory.STRING);
  }

  /** This supports edge cases like GRPC Metadata propagation which doesn't use String keys. */
  abstract public Propagation.Factory propagationFactory();

  /**
   * Sampler is responsible for deciding if a particular trace should be "sampled", i.e. whether the
   * overhead of tracing will occur and/or if a trace will be reported to Zipkin.
   *
   * @see Tracer#nextSpan(SamplerFunction, Object) for temporary overrides
   */
  abstract public Sampler sampler();

  /**
   * This supports in-process propagation, typically across thread boundaries. This includes
   * utilities for concurrent types like {@linkplain java.util.concurrent.ExecutorService}.
   */
  abstract public CurrentTraceContext currentTraceContext();

  /**
   * This exposes the microsecond clock used by operations such as {@link Span#finish()}. This is
   * helpful when you want to time things manually. Notably, this clock will be coherent for all
   * child spans in this trace (that use this tracing component). For example, NTP or system clock
   * changes will not affect the result.
   *
   * @param context references a potentially unstarted span you'd like a clock correlated with
   */
  public final Clock clock(TraceContext context) {
    return tracer().pendingSpans.getOrCreate(null, context, false).clock();
  }

  abstract public ErrorParser errorParser();

  /**
   * Returns the most recently created tracing component iff it hasn't been closed. null otherwise.
   *
   * <p>This object should not be cached.
   */
  @Nullable public static Tracing current() {
    return CURRENT.get();
  }

  /**
   * Returns the most recently created tracer if its component hasn't been closed. null otherwise.
   *
   * <p>This object should not be cached.
   */
  @Nullable public static Tracer currentTracer() {
    Tracing tracing = current();
    return tracing != null ? tracing.tracer() : null;
  }

  /**
   * When true, no recording is done and nothing is reported to zipkin. However, trace context is
   * still injected into outgoing requests.
   *
   * @see Span#isNoop()
   */
  public abstract boolean isNoop();

  /**
   * Set true to drop data and only return {@link Span#isNoop() noop spans} regardless of sampling
   * policy. This allows operators to stop tracing in risk scenarios.
   *
   * @see #isNoop()
   */
  public abstract void setNoop(boolean noop);

  /** Ensures this component can be garbage collected, by making it not {@link #current()} */
  @Override abstract public void close();

  public static final class Builder {
    String localServiceName = "unknown", localIp;
    int localPort; // zero means null
    Reporter<zipkin2.Span> spanReporter;
    Clock clock;
    Sampler sampler = Sampler.ALWAYS_SAMPLE;
    CurrentTraceContext currentTraceContext = CurrentTraceContext.Default.inheritable();
    boolean traceId128Bit = false, supportsJoin = true, alwaysReportSpans = false;
    boolean trackOrphans = false;
    Propagation.Factory propagationFactory = B3Propagation.FACTORY;
    ErrorParser errorParser = new ErrorParser();
    Set<FinishedSpanHandler> finishedSpanHandlers = new LinkedHashSet<>(); // dupes not ok

    /**
     * Label of the remote node in the service graph, such as "favstar". Avoid names with variables
     * or unique identifiers embedded. Defaults to "unknown".
     *
     * <p>This is a primary label for trace lookup and aggregation, so it should be intuitive and
     * consistent. Many use a name from service discovery.
     *
     * @see #localIp(String)
     */
    public Builder localServiceName(String localServiceName) {
      if (localServiceName == null || localServiceName.isEmpty()) {
        throw new IllegalArgumentException(localServiceName + " is not a valid serviceName");
      }
      this.localServiceName = localServiceName;
      return this;
    }

    /**
     * The text representation of the primary IP address associated with this service. Ex.
     * 192.168.99.100 or 2001:db8::c001. Defaults to a link local IP.
     *
     * @see #localServiceName(String)
     * @see #localPort(int)
     * @since 5.2
     */
    public Builder localIp(String localIp) {
      String maybeIp = IpLiteral.ipOrNull(localIp);
      if (maybeIp == null) throw new IllegalArgumentException(localIp + " is not a valid IP");
      this.localIp = maybeIp;
      return this;
    }

    /**
     * The primary listen port associated with this service. No default.
     *
     * @see #localIp(String)
     * @since 5.2
     */
    public Builder localPort(int localPort) {
      if (localPort > 0xffff) throw new IllegalArgumentException("invalid localPort " + localPort);
      if (localPort < 0) localPort = 0;
      this.localPort = localPort;
      return this;
    }

    /**
     * Sets the {@link zipkin2.Span#localEndpoint() Endpoint of the local service} being traced.
     *
     * @deprecated Use {@link #localServiceName(String)} {@link #localIp(String)} and {@link
     * #localPort(int)}. Will be removed in Brave v6.
     */
    @Deprecated
    public Builder endpoint(zipkin2.Endpoint endpoint) {
      if (endpoint == null) throw new NullPointerException("endpoint == null");
      this.localServiceName = endpoint.serviceName();
      this.localIp = endpoint.ipv6() != null ? endpoint.ipv6() : endpoint.ipv4();
      this.localPort = endpoint.portAsInt();
      return this;
    }

    /**
     * Controls how {@linkplain TraceContext#sampled() remote sampled} spans report using the
     * <a href="https://github.com/openzipkin/zipkin-reporter-java">io.zipkin.reporter2:zipkin-reporter</a>
     * library. This input is usually a {@link AsyncReporter} which batches spans before reporting
     * them.
     *
     * <p>For example, here's how to batch send spans via http:
     *
     * <pre>{@code
     * spanReporter = AsyncReporter.create(URLConnectionSender.create("http://localhost:9411/api/v2/spans"));
     *
     * tracingBuilder.spanReporter(spanReporter);
     * }</pre>
     *
     * <p>See https://github.com/openzipkin/zipkin-reporter-java
     *
     * <h3>Relationship to {@link #addFinishedSpanHandler(FinishedSpanHandler)}</h3>
     * There may only be one {@code spanReporter}. When present, this will be converted as the
     * <em>last</em> {@link FinishedSpanHandler}. This ensures any customization or redaction
     * happen spans are sent out of process.
     *
     * @see #addFinishedSpanHandler(FinishedSpanHandler)
     */
    public Builder spanReporter(Reporter<zipkin2.Span> spanReporter) {
      if (spanReporter == null) throw new NullPointerException("spanReporter == null");
      this.spanReporter = spanReporter;
      return this;
    }

    /**
     * Assigns microsecond-resolution timestamp source for operations like {@link Span#start()}.
     * Defaults to JRE-specific platform time.
     *
     * <p>Note: timestamps are read once per trace, then {@link System#nanoTime() ticks}
     * thereafter. This ensures there's no clock skew problems inside a single trace.
     *
     * See {@link Tracing#clock(TraceContext)}
     */
    public Builder clock(Clock clock) {
      if (clock == null) throw new NullPointerException("clock == null");
      this.clock = clock;
      return this;
    }

    /**
     * Sampler is responsible for deciding if a particular trace should be "sampled", i.e. whether
     * the overhead of tracing will occur and/or if a trace will be reported to Zipkin.
     *
     * @see Tracer#nextSpan(SamplerFunction, Object) for temporary overrides
     */
    public Builder sampler(Sampler sampler) {
      if (sampler == null) throw new NullPointerException("sampler == null");
      this.sampler = sampler;
      return this;
    }

    /**
     * Responsible for implementing {@link Tracer#startScopedSpan(String)}, {@link
     * Tracer#currentSpanCustomizer()}, {@link Tracer#currentSpan()} and {@link
     * Tracer#withSpanInScope(Span)}.
     *
     * <p>By default a simple thread-local is used. Override to support other mechanisms or to
     * synchronize with other mechanisms such as SLF4J's MDC.
     */
    public Builder currentTraceContext(CurrentTraceContext currentTraceContext) {
      if (currentTraceContext == null) {
        throw new NullPointerException("currentTraceContext == null");
      }
      this.currentTraceContext = currentTraceContext;
      return this;
    }

    /**
     * Controls how trace contexts are injected or extracted from remote requests, such as from http
     * headers. Defaults to {@link B3Propagation#FACTORY}
     */
    public Builder propagationFactory(Propagation.Factory propagationFactory) {
      if (propagationFactory == null) throw new NullPointerException("propagationFactory == null");
      this.propagationFactory = propagationFactory;
      return this;
    }

    /** When true, new root spans will have 128-bit trace IDs. Defaults to false (64-bit) */
    public Builder traceId128Bit(boolean traceId128Bit) {
      this.traceId128Bit = traceId128Bit;
      return this;
    }

    /**
     * True means the tracing system supports sharing a span ID between a {@link Span.Kind#CLIENT}
     * and {@link Span.Kind#SERVER} span. Defaults to true.
     *
     * <p>Set this to false when the tracing system requires the opposite. For example, if
     * ultimately spans are sent to Amazon X-Ray or Google Stackdriver Trace, you should set this to
     * false.
     *
     * <p>This is implicitly set to false when {@link Propagation.Factory#supportsJoin()} is false,
     * as in that case, sharing IDs isn't possible anyway.
     *
     * @see Propagation.Factory#supportsJoin()
     */
    public Builder supportsJoin(boolean supportsJoin) {
      this.supportsJoin = supportsJoin;
      return this;
    }

    public Builder errorParser(ErrorParser errorParser) {
      this.errorParser = errorParser;
      return this;
    }

    /**
     * Inputs receive {code (context, span)} pairs for every {@linkplain TraceContext#sampledLocal()
     * locally sampled} span. The span is mutable for customization or redaction purposes and
     * handlers execute in order: If any handler returns {code false}, the next will not see the
     * span.
     *
     * <h3>Advanced notes</h3>
     *
     * <p>When {@link FinishedSpanHandler#alwaysSampleLocal()} is {@code true}, handlers here
     * receive data even when spans are not sampled remotely. This setting is often used for metrics
     * aggregation.
     *
     * <h3>Relationship to {@link #spanReporter(Reporter)}</h3>
     * This is similar to {@link #spanReporter(Reporter)} except for a few things:
     * <ul>
     *   <li>This is decoupled from Zipkin types</li>
     *   <li>This receives both {@linkplain TraceContext#sampled() remote} and {@linkplain TraceContext#sampledLocal() local} sampled spans</li>
     *   <li>{@link MutableSpan} is mutable and has a raw {@link Throwable} error</li>
     *   <li>{@link TraceContext} allows late lookup of {@linkplain BaggageField baggage}</li>
     * </ul>
     *
     * <p>This is used to create more efficient or completely different data structures than what's
     * provided in the {@code io.zipkin.reporter2:zipkin-reporter} library. For example, you can use
     * a higher performance codec or disruptor, or you can forward to a vendor-specific trace exporter.
     *
     * @param handler skipped if {@link FinishedSpanHandler#NOOP} or already added
     * @see #spanReporter(Reporter)
     * @see #alwaysReportSpans()
     * @see TraceContext#sampledLocal()
     */
    public Builder addFinishedSpanHandler(FinishedSpanHandler handler) {
      if (handler == null) throw new NullPointerException("finishedSpanHandler == null");
      if (handler != FinishedSpanHandler.NOOP) { // lenient on config bug
        if (!finishedSpanHandlers.add(handler)) {
          Platform.get().log("Please check configuration as %s was added twice", handler, null);
        }
      }
      return this;
    }

    /**
     * When true, all spans {@link TraceContext#sampledLocal() sampled locally} are reported to the
     * {@link #spanReporter(Reporter) span reporter}, even if they aren't sampled remotely. Defaults
     * to false.
     *
     * <p>The primary use case is to implement a <a href="https://github.com/openzipkin-contrib/zipkin-secondary-sampling">sampling
     * overlay</a>, such as boosting the sample rate for a subset of the network depending on the
     * value of a {@link BaggageField baggage field}. This means that data will report when either
     * the trace is normally sampled, or secondarily sampled via a custom header.
     *
     * <p>This is simpler than {@link #addFinishedSpanHandler(FinishedSpanHandler)}, because you
     * don't have to duplicate transport mechanics already implemented in the {@link
     * #spanReporter(Reporter) span reporter}. However, this assumes your backend can properly
     * process the partial traces implied when using conditional sampling. For example, if your
     * sampling condition is not consistent on a call tree, the resulting data could appear broken.
     *
     * @see #addFinishedSpanHandler(FinishedSpanHandler)
     * @see TraceContext#sampledLocal()
     * @since 5.8
     */
    public Builder alwaysReportSpans() {
      this.alwaysReportSpans = true;
      return this;
    }

    /**
     * When true, this logs the caller which orphaned a span to the category "brave.Tracer" at
     * {@link Level#FINE}. Defaults to false.
     *
     * <p>If you see data with the annotation "brave.flush", you may have an instrumentation bug.
     * To see which code was involved, set this and ensure the logger {@link Tracing} is at {@link
     * Level#FINE}. Do not do this in production as tracking orphaned data incurs higher overhead.
     *
     * @see FinishedSpanHandler#supportsOrphans()
     * @since 5.9
     */
    public Builder trackOrphans() {
      this.trackOrphans = true;
      return this;
    }

    public Tracing build() {
      return new Default(this);
    }

    Builder() {
    }
  }

  static final class Default extends Tracing {
    final Tracer tracer;
    final Propagation.Factory propagationFactory;
    final Propagation<String> stringPropagation;
    final CurrentTraceContext currentTraceContext;
    final Sampler sampler;
    final Clock clock;
    final ErrorParser errorParser;
    final AtomicBoolean noop;

    Default(Builder builder) {
      this.clock = builder.clock != null ? builder.clock : Platform.get().clock();
      this.errorParser = builder.errorParser;
      this.propagationFactory = builder.propagationFactory;
      this.stringPropagation = builder.propagationFactory.create(Propagation.KeyFactory.STRING);
      this.currentTraceContext = builder.currentTraceContext;
      this.sampler = builder.sampler;
      this.noop = new AtomicBoolean();

      MutableSpan defaultSpan = new MutableSpan();
      defaultSpan.localServiceName(builder.localServiceName);
      defaultSpan.localIp(builder.localIp != null ? builder.localIp : Platform.get().linkLocalIp());
      defaultSpan.localPort(builder.localPort);

      FinishedSpanHandler zipkinHandler = builder.spanReporter != Reporter.NOOP
        ? new ZipkinFinishedSpanHandler(defaultSpan, builder.spanReporter, errorParser,
        builder.alwaysReportSpans)
        : FinishedSpanHandler.NOOP;

      FinishedSpanHandler finishedSpanHandler =
        zipkinReportingFinishedSpanHandler(builder.finishedSpanHandlers, zipkinHandler, noop);

      Set<FinishedSpanHandler> orphanedSpanHandlers = new LinkedHashSet<>();
      for (FinishedSpanHandler handler : builder.finishedSpanHandlers) {
        if (handler.supportsOrphans()) orphanedSpanHandlers.add(handler);
      }

      FinishedSpanHandler orphanedSpanHandler = finishedSpanHandler;
      boolean allHandlersSupportOrphans = builder.finishedSpanHandlers.equals(orphanedSpanHandlers);
      if (!allHandlersSupportOrphans) {
        orphanedSpanHandler =
          zipkinReportingFinishedSpanHandler(orphanedSpanHandlers, zipkinHandler, noop);
      }

      PendingSpans pendingSpans =
        new PendingSpans(defaultSpan, clock, orphanedSpanHandler, builder.trackOrphans, noop);

      this.tracer = new Tracer(
        builder.clock,
        builder.propagationFactory,
        finishedSpanHandler,
        pendingSpans,
        builder.sampler,
        builder.currentTraceContext,
        builder.traceId128Bit || propagationFactory.requires128BitTraceId(),
        builder.supportsJoin && propagationFactory.supportsJoin(),
        finishedSpanHandler.alwaysSampleLocal(),
        noop
      );
      // assign current IFF there's no instance already current
      CURRENT.compareAndSet(null, this);
    }

    @Override public Tracer tracer() {
      return tracer;
    }

    @Override public Propagation<String> propagation() {
      return stringPropagation;
    }

    @Override public Propagation.Factory propagationFactory() {
      return propagationFactory;
    }

    @Override public Sampler sampler() {
      return sampler;
    }

    @Override public CurrentTraceContext currentTraceContext() {
      return currentTraceContext;
    }

    @Override public ErrorParser errorParser() {
      return errorParser;
    }

    @Override public boolean isNoop() {
      return noop.get();
    }

    @Override public void setNoop(boolean noop) {
      this.noop.set(noop);
    }

    @Override public String toString() {
      return tracer.toString();
    }

    @Override public void close() {
      // only set null if we are the outer-most instance
      CURRENT.compareAndSet(this, null);
    }
  }

  static FinishedSpanHandler zipkinReportingFinishedSpanHandler(
    Set<FinishedSpanHandler> input, FinishedSpanHandler zipkinHandler, AtomicBoolean noop) {
    ArrayList<FinishedSpanHandler> defensiveCopy = new ArrayList<>(input);
    // When present, the Zipkin handler is invoked after the user-supplied finished span handlers.
    if (zipkinHandler != FinishedSpanHandler.NOOP) defensiveCopy.add(zipkinHandler);

    // Make sure any exceptions caused by handlers don't crash callers
    return NoopAwareFinishedSpanHandler.create(defensiveCopy, noop);
  }

  Tracing() { // intentionally hidden constructor
  }
}
