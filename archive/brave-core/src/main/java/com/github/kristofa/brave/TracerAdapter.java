package com.github.kristofa.brave;

import brave.Clock;
import brave.Tracer;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import com.github.kristofa.brave.internal.InternalSpan;
import com.github.kristofa.brave.internal.Nullable;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import zipkin.Constants;

/**
 * This is a bridge between the new {@linkplain Tracer} api in {@code io.zipkin.brave:brave} and the
 * former model defined prior.
 *
 * <p>This class uses package-protected hooks to integrate, and is optional. For example, those not
 * using {@linkplain Tracer} do not have a runtime dependency on {@code io.zipkin.brave:brave}.
 */
public final class TracerAdapter {
  /** The endpoint in {@linkplain ServerClientAndLocalSpanState} is no longer used. */
  static final Endpoint DUMMY_ENDPOINT = Endpoint.builder().serviceName("not used").build();

  public static Brave newBrave(Tracer tracer) {
    if (tracer == null) throw new NullPointerException("tracer == null");
    return newBrave(tracer, new ThreadLocalServerClientAndLocalSpanState(DUMMY_ENDPOINT));
  }

  /**
   * Constructs a new Brave instance that sends traces to the provided tracer.
   *
   * @param state for in-process propagation. Note {@link CommonSpanState#endpoint()} is ignored.
   */
  public static Brave newBrave(Tracer tracer, ServerClientAndLocalSpanState state) {
    if (tracer == null) throw new NullPointerException("tracer == null");
    if (state == null) throw new NullPointerException("state == null");
    Clock delegate = tracer.clock();
    return new Brave.Builder(state)
        .clock(new AnnotationSubmitter.Clock() {
          @Override public long currentTimeMicroseconds() {
            return delegate.currentTimeMicroseconds();
          }

          @Override public String toString() {
            return delegate.toString();
          }
        })
        .spanFactory(new Brave4SpanFactory(tracer))
        .recorder(new Brave4Recorder(tracer)).build();
  }

  public static brave.Span toSpan(Tracer tracer, Span span) {
    if (tracer == null) throw new NullPointerException("tracer == null");
    return tracer.toSpan(toTraceContext(Brave.context(span)));
  }

  public static brave.Span toSpan(Tracer tracer, SpanId spanId) {
    if (tracer == null) throw new NullPointerException("tracer == null");
    return tracer.toSpan(toTraceContext(spanId));
  }

  /**
   * Returns a span representing the sampled status of the current server span or null if there's no
   * span attached to the current thread.
   */
  @Nullable
  public static brave.Span getServerSpan(Tracer tracer, ServerSpanThreadBinder threadBinder) {
    if (tracer == null) throw new NullPointerException("tracer == null");
    if (threadBinder == null) throw new NullPointerException("threadBinder == null");
    ServerSpan result = threadBinder.getCurrentServerSpan();
    if (result == null || result.equals(ServerSpan.EMPTY)) return null;
    if (result.getSpan() != null) return toSpan(tracer, result.getSpan());
    assert result.getSample() != null && !result.getSample() : "unexpected sample state: " + result;
    return tracer.newTrace(SamplingFlags.NOT_SAMPLED);
  }

  /** Sets a span associated with the context as the current server span. */
  public static void setServerSpan(TraceContext context, ServerSpanThreadBinder threadBinder) {
    if (threadBinder == null) throw new NullPointerException("threadBinder == null");
    ServerSpan serverSpan = ServerSpan.create(toSpan(context));
    threadBinder.setCurrentSpan(serverSpan);
  }

  public static TraceContext toTraceContext(SpanId spanId) { // visible for testing
    if (spanId == null) throw new NullPointerException("spanId == null");
    return TraceContext.newBuilder()
        .traceIdHigh(spanId.traceIdHigh)
        .traceId(spanId.traceId)
        .parentId(spanId.nullableParentId())
        .spanId(spanId.spanId)
        .debug(spanId.debug())
        .sampled(spanId.sampled()).build();
  }

  public static Span toSpan(TraceContext context) {
    if (context == null) throw new NullPointerException("context == null");
    return InternalSpan.instance.toSpan(toSpanId(context));
  }

  static final class Brave4SpanFactory extends SpanFactory {
    final Tracer delegate;

    Brave4SpanFactory(Tracer tracer) {
      this.delegate = tracer;
    }

    @Override Span nextSpan(@Nullable SpanId maybeParent) {
      brave.Span span = maybeParent != null
          ? delegate.newChild(toTraceContext(maybeParent))
          : delegate.newTrace();
      return Brave.toSpan(toSpanId(span.context()));
    }

    @Override Span joinSpan(SpanId spanId) {
      TraceContext context = toTraceContext(spanId);
      return Brave.toSpan(toSpanId(delegate.joinSpan(context).context()));
    }
  }

  static final class Brave4Recorder extends Recorder {
    final Tracer tracer;

    Brave4Recorder(Tracer tracer) {
      this.tracer = tracer;
    }

    @Override void name(Span span, String name) {
      brave4(span).name(name);
    }

    @Override void start(Span span, long timestamp) {
      synchronized (span) { // make visible to old api
        span.setTimestamp(timestamp);
      }
      brave4(span).start(timestamp);
    }

    @Override void annotate(Span span, long timestamp, String value) {
      brave4(span).annotate(timestamp, value);
    }

    @Override void address(Span span, String key, Endpoint endpoint) {
      brave.Span brave4 = brave4(span);
      switch (key) {
        case Constants.SERVER_ADDR:
          brave4.kind(brave.Span.Kind.CLIENT);
          break;
        case Constants.CLIENT_ADDR:
          brave4.kind(brave.Span.Kind.SERVER);
          break;
        default:
          throw new AssertionError(key + " is not yet supported");
      }
      brave4.remoteEndpoint(zipkin.Endpoint.builder()
          .serviceName(endpoint.service_name)
          .ipv4(endpoint.ipv4)
          .ipv6(endpoint.ipv6)
          .port(endpoint.port)
          .build());
    }

    @Override void tag(Span span, String key, String value) {
      brave4(span).tag(key, value);
    }

    @Override void finish(Span span, long timestamp) {
      brave4(span).finish(timestamp);
    }

    @Override void flush(Span span) {
      brave4(span).flush();
    }

    // If garbage becomes a concern, we can introduce caching or thread locals.
    brave.Span brave4(Span span) {
      return tracer.toSpan(toTraceContext(InternalSpan.instance.context(span)));
    }

    @Override public long currentTimeMicroseconds() {
      return tracer.clock().currentTimeMicroseconds();
    }
  }

  static SpanId toSpanId(TraceContext context) {
    return SpanId.builder()
        .traceIdHigh(context.traceIdHigh())
        .traceId(context.traceId())
        .parentId(context.parentId())
        .spanId(context.spanId())
        .debug(context.debug())
        .sampled(context.sampled()).build();
  }
}
