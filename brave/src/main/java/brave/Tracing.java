/*
 * Copyright 2013-2019 The OpenZipkin Authors
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

import brave.handler.FinishedSpanHandler;
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
import java.io.Closeable;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Reporter;
import zipkin2.reporter.Sender;

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
   * @see Tracer#withSampler(Sampler)
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
    return tracer().pendingSpans.getOrCreate(context, false).clock();
  }

  abstract public ErrorParser errorParser();

  // volatile for visibility on get. writes guarded by Tracing.class
  static volatile Tracing current = null;

  /**
   * Returns the most recently created tracer if its component hasn't been closed. null otherwise.
   *
   * <p>This object should not be cached.
   */
  @Nullable public static Tracer currentTracer() {
    Tracing tracing = current;
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

  /**
   * Returns the most recently created tracing component iff it hasn't been closed. null otherwise.
   *
   * <p>This object should not be cached.
   */
  @Nullable public static Tracing current() {
    return current;
  }

  /** Ensures this component can be garbage collected, by making it not {@link #current()} */
  @Override abstract public void close();

  public static final class Builder {
    String localServiceName = "unknown", localIp;
    int localPort; // zero means null
    Reporter<zipkin2.Span> spanReporter;
    Clock clock;
    Sampler sampler = Sampler.ALWAYS_SAMPLE;
    CurrentTraceContext currentTraceContext = CurrentTraceContext.Default.inheritable();
    boolean traceId128Bit = false, supportsJoin = true;
    Propagation.Factory propagationFactory = B3Propagation.FACTORY;
    ErrorParser errorParser = new ErrorParser();
    // Intentional dupes would be surprising. Be very careful when adding features here that no
    // other list parameter extends FinishedSpanHandler. If it did, it could be accidentally added
    // result in dupes here. Any component that provides a FinishedSpanHandler needs to be
    // explicitly documented to not be added also here, to avoid dupes. That or mark a provider
    // method protected, but still the user could accidentally create a dupe by misunderstanding and
    // overriding public so they can add it here.
    ArrayList<FinishedSpanHandler> finishedSpanHandlers = new ArrayList<>();

    /**
     * Lower-case label of the remote node in the service graph, such as "favstar". Avoid names with
     * variables or unique identifiers embedded. Defaults to "unknown".
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
      this.localServiceName = localServiceName.toLowerCase(Locale.ROOT);
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
     * Controls how spans are reported. Defaults to logging, but often an {@link AsyncReporter}
     * which batches spans before sending to Zipkin.
     *
     * The {@link AsyncReporter} includes a {@link Sender}, which is a driver for transports like
     * http, kafka and scribe.
     *
     * <p>For example, here's how to batch send spans via http:
     *
     * <pre>{@code
     * spanReporter = AsyncReporter.create(URLConnectionSender.create("http://localhost:9411/api/v2/spans"));
     *
     * tracingBuilder.spanReporter(spanReporter);
     * }</pre>
     *
     * <p>See https://github.com/apache/incubator-zipkin-reporter-java
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
     * @see Tracer#withSampler(Sampler) for temporary overrides
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
     * Similar to {@link #spanReporter(Reporter)} except it can read the trace context and create
     * more efficient or completely different data structures. Importantly, the input is mutable for
     * customization purposes.
     *
     * <p>These handlers execute before the {@link #spanReporter(Reporter) span reporter}, which
     * means any mutations occur prior to Zipkin.
     *
     * <h3>Advanced notes</h3>
     *
     * <p>This is named firehose as it can receive data even when spans are not sampled remotely.
     * For example, {@link FinishedSpanHandler#alwaysSampleLocal()} will generate data for all
     * traced requests while not affecting headers. This setting is often used for metrics
     * aggregation.
     *
     *
     * <p>Your handler can also be a custom span transport. When this is the case, set the {@link
     * #spanReporter(Reporter) span reporter} to {@link Reporter#NOOP} to avoid redundant conversion
     * overhead.
     *
     * @see TraceContext#sampledLocal()
     */
    public Builder addFinishedSpanHandler(FinishedSpanHandler finishedSpanHandler) {
      if (finishedSpanHandler == null) {
        throw new NullPointerException("finishedSpanHandler == null");
      }
      if (finishedSpanHandler != FinishedSpanHandler.NOOP) { // lenient on config bug
        this.finishedSpanHandlers.add(finishedSpanHandler);
      }
      return this;
    }

    public Tracing build() {
      if (clock == null) clock = Platform.get().clock();
      if (localIp == null) localIp = Platform.get().linkLocalIp();
      if (spanReporter == null) spanReporter = new LoggingReporter();
      return new Default(this);
    }

    Builder() {
    }
  }

  static final class LoggingReporter implements Reporter<zipkin2.Span> {
    final Logger logger = Logger.getLogger(Tracer.class.getName());

    @Override public void report(zipkin2.Span span) {
      if (span == null) throw new NullPointerException("span == null");
      if (!logger.isLoggable(Level.INFO)) return;
      logger.info(span.toString());
    }

    @Override public String toString() {
      return "LoggingReporter{name=" + logger.getName() + "}";
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
      this.clock = builder.clock;
      this.errorParser = builder.errorParser;
      this.propagationFactory = builder.propagationFactory;
      this.stringPropagation = builder.propagationFactory.create(Propagation.KeyFactory.STRING);
      this.currentTraceContext = builder.currentTraceContext;
      this.sampler = builder.sampler;
      this.noop = new AtomicBoolean();

      FinishedSpanHandler zipkinHandler = builder.spanReporter != Reporter.NOOP
        ? new ZipkinFinishedSpanHandler(builder.spanReporter, errorParser,
        builder.localServiceName, builder.localIp, builder.localPort)
        : FinishedSpanHandler.NOOP;

      FinishedSpanHandler finishedSpanHandler =
        zipkinReportingFinishedSpanHandler(builder.finishedSpanHandlers, zipkinHandler, noop);

      ArrayList<FinishedSpanHandler> orphanedSpanHandlers = new ArrayList<>();
      for (FinishedSpanHandler handler : builder.finishedSpanHandlers) {
        if (handler.supportsOrphans()) orphanedSpanHandlers.add(handler);
      }

      FinishedSpanHandler orphanedSpanHandler = finishedSpanHandler;
      boolean allHandlersSupportOrphans = builder.finishedSpanHandlers.equals(orphanedSpanHandlers);
      if (!allHandlersSupportOrphans) {
        orphanedSpanHandler =
          zipkinReportingFinishedSpanHandler(orphanedSpanHandlers, zipkinHandler, noop);
      }

      this.tracer = new Tracer(
        builder.clock,
        builder.propagationFactory,
        finishedSpanHandler,
        new PendingSpans(clock, orphanedSpanHandler, noop),
        builder.sampler,
        builder.currentTraceContext,
        builder.traceId128Bit || propagationFactory.requires128BitTraceId(),
        builder.supportsJoin && propagationFactory.supportsJoin(),
        finishedSpanHandler.alwaysSampleLocal(),
        noop
      );
      maybeSetCurrent();
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

    private void maybeSetCurrent() {
      if (current != null) return;
      synchronized (Tracing.class) {
        if (current == null) current = this;
      }
    }

    @Override public String toString() {
      return tracer.toString();
    }

    @Override public void close() {
      if (current != this) return;
      // don't blindly set most recent to null as there could be a race
      synchronized (Tracing.class) {
        if (current == this) current = null;
      }
    }
  }

  static FinishedSpanHandler zipkinReportingFinishedSpanHandler(
    ArrayList<FinishedSpanHandler> input, FinishedSpanHandler zipkinHandler, AtomicBoolean noop) {
    ArrayList<FinishedSpanHandler> defensiveCopy = new ArrayList<>(input);
    // When present, the Zipkin handler is invoked after the user-supplied finished span handlers.
    if (zipkinHandler != FinishedSpanHandler.NOOP) defensiveCopy.add(zipkinHandler);

    // Make sure any exceptions caused by handlers don't crash callers
    return NoopAwareFinishedSpanHandler.create(defensiveCopy, noop);
  }

  Tracing() { // intentionally hidden constructor
  }
}
