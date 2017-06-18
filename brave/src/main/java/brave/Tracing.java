package brave;

import brave.internal.Internal;
import brave.internal.Platform;
import brave.propagation.CurrentTraceContext;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import java.io.Closeable;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import zipkin.Endpoint;
import zipkin.reporter.AsyncReporter;
import zipkin.reporter.Reporter;
import zipkin.reporter.Sender;

/**
 * This provides utilities needed for trace instrumentation. For example, a {@link Tracer}.
 *
 * <p>Instances built via {@link #newBuilder()} are registered automatically such that statically
 * configured instrumentation like JDBC drivers can use {@link #current()}.
 *
 * <p>This type can be extended so that the object graph can be built differently or overridden, for
 * example via spring or when mocking.
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
   * This supports in-process propagation, typically across thread boundaries. This includes
   * utilities for concurrent types like {@linkplain java.util.concurrent.ExecutorService}.
   */
  abstract public CurrentTraceContext currentTraceContext();

  /**
   * This exposes the microsecond clock used by operations such as {@link Span#finish()}. This is
   * helpful when you want to time things manually.
   */
  abstract public Clock clock();

  // volatile for visibility on get. writes guarded by Tracing.class
  static volatile Tracing current = null;

  /**
   * Returns the most recently created tracer iff its component hasn't been closed. null otherwise.
   *
   * <p>This object should not be cached.
   */
  @Nullable public static Tracer currentTracer() {
    Tracing tracing = current;
    return tracing != null ? tracing.tracer() : null;
  }

  final AtomicBoolean noop = new AtomicBoolean(false);

  /**
   * When true, no recording is done and nothing is reported to zipkin. However, trace context is
   * still injected into outgoing requests.
   *
   * @see Span#isNoop()
   */
  public boolean isNoop() {
    return noop.get();
  }

  /**
   * Set true to drop data and only return {@link Span#isNoop() noop spans} regardless of sampling
   * policy. This allows operators to stop tracing in risk scenarios.
   *
   * @see #isNoop()
   */
  public void setNoop(boolean noop) {
    this.noop.set(noop);
  }

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
    String localServiceName;
    Endpoint localEndpoint;
    Reporter<zipkin.Span> reporter;
    Clock clock;
    Sampler sampler = Sampler.ALWAYS_SAMPLE;
    CurrentTraceContext currentTraceContext = new CurrentTraceContext.Default();
    boolean traceId128Bit = false;
    Propagation.Factory propagationFactory = Propagation.Factory.B3;

    /**
     * Controls the name of the service being traced, while still using a default site-local IP.
     * This is an alternative to {@link #localEndpoint(Endpoint)}.
     *
     * @param localServiceName name of the service being traced. Defaults to "unknown".
     */
    public Builder localServiceName(String localServiceName) {
      if (localServiceName == null) throw new NullPointerException("localServiceName == null");
      this.localServiceName = localServiceName;
      return this;
    }

    /**
     * @param localEndpoint Endpoint of the local service being traced. Defaults to site local.
     */
    public Builder localEndpoint(Endpoint localEndpoint) {
      if (localEndpoint == null) throw new NullPointerException("localEndpoint == null");
      this.localEndpoint = localEndpoint;
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
     * reporter = AsyncReporter.create(URLConnectionSender.create("http://localhost:9411/api/v1/spans"));
     *
     * tracerBuilder.reporter(reporter);
     * }</pre>
     *
     * <p>See https://github.com/openzipkin/zipkin-reporter-java
     */
    public Builder reporter(Reporter<zipkin.Span> reporter) {
      if (reporter == null) throw new NullPointerException("reporter == null");
      this.reporter = reporter;
      return this;
    }

    /** See {@link Tracing#clock()} */
    public Builder clock(Clock clock) {
      if (clock == null) throw new NullPointerException("clock == null");
      this.clock = clock;
      return this;
    }

    /**
     * Sampler is responsible for deciding if a particular trace should be "sampled", i.e. whether
     * the overhead of tracing will occur and/or if a trace will be reported to Zipkin.
     */
    public Builder sampler(Sampler sampler) {
      this.sampler = sampler;
      return this;
    }

    /**
     * Responsible for implementing {@link Tracer#currentSpan()} and {@link
     * Tracer#withSpanInScope(Span)}. By default a simple thread-local is used. Override to support
     * other mechanisms or to synchronize with other mechanisms such as SLF4J's MDC.
     */
    public Builder currentTraceContext(CurrentTraceContext currentTraceContext) {
      this.currentTraceContext = currentTraceContext;
      return this;
    }

    /** When true, new root spans will have 128-bit trace IDs. Defaults to false (64-bit) */
    public Builder traceId128Bit(boolean traceId128Bit) {
      this.traceId128Bit = traceId128Bit;
      return this;
    }

    public Tracing build() {
      if (clock == null) clock = Platform.get();
      if (localEndpoint == null) {
        localEndpoint = Platform.get().localEndpoint();
        if (localServiceName != null) {
          localEndpoint = localEndpoint.toBuilder().serviceName(localServiceName).build();
        }
      }
      if (reporter == null) reporter = Platform.get();

      return new Default(this);
    }

    Builder() {
    }
  }

  static {
    Internal.instance = new Internal() {
      @Override public Long timestamp(Tracer tracer, TraceContext context) {
        return tracer.recorder.timestamp(context);
      }
    };
  }

  static final class Default extends Tracing {
    final Tracer tracer;
    final Propagation.Factory propagationFactory;
    final Propagation<String> stringPropagation;
    final CurrentTraceContext currentTraceContext;
    final Clock clock;

    Default(Builder builder) {
      this.tracer = new Tracer(builder, noop);
      this.propagationFactory = builder.propagationFactory;
      this.stringPropagation = builder.propagationFactory.create(Propagation.KeyFactory.STRING);
      this.currentTraceContext = builder.currentTraceContext;
      this.clock = builder.clock;
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

    @Override public CurrentTraceContext currentTraceContext() {
      return currentTraceContext;
    }

    @Override public Clock clock() {
      return clock;
    }

    private void maybeSetCurrent() {
      if (current != null) return;
      synchronized (Tracing.class) {
        if (current == null) current = this;
      }
    }

    @Override public void close() {
      if (current != this) return;
      // don't blindly set most recent to null as there could be a race
      synchronized (Tracing.class) {
        if (current == this) current = null;
      }
    }
  }
}
