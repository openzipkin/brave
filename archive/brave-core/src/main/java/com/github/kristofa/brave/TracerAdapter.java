package com.github.kristofa.brave;

import brave.Clock;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import com.github.kristofa.brave.internal.InternalSpan;
import com.github.kristofa.brave.internal.Nullable;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;

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

  public static Brave newBrave(Tracing tracing) {
    if (tracing == null) throw new NullPointerException("tracer == null");
    return newBrave(tracing, new ThreadLocalServerClientAndLocalSpanState(DUMMY_ENDPOINT));
  }

  /**
   * Constructs a new Brave instance that sends traces to the provided tracer.
   *
   * @param state for in-process propagation. Note {@link CommonSpanState#endpoint()} is ignored.
   */
  public static Brave newBrave(Tracing tracing, ServerClientAndLocalSpanState state) {
    if (tracing == null) throw new NullPointerException("tracer == null");
    if (state == null) throw new NullPointerException("state == null");
    Clock delegate = brave.internal.Platform.get().clock();
    return new Brave.Builder(state)
        .clock(new AnnotationSubmitter.Clock() {
          @Override public long currentTimeMicroseconds() {
            return delegate.currentTimeMicroseconds();
          }

          @Override public String toString() {
            return delegate.toString();
          }
        })
        .spanFactory(new Brave4SpanFactory(tracing))
        .recorder(new Brave4Recorder(tracing)).build();
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
    return tracer.withSampler(Sampler.NEVER_SAMPLE).newTrace();
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
    final Tracer tracer;

    Brave4SpanFactory(Tracing tracing) {
      this.tracer = tracing.tracer();
    }

    @Override Span nextSpan(@Nullable SpanId maybeParent) {
      brave.Span span = maybeParent != null
          ? tracer.newChild(toTraceContext(maybeParent))
          : tracer.newTrace();
      return Brave.toSpan(toSpanId(span.context()));
    }

    @Override Span joinSpan(SpanId spanId) {
      TraceContext context = toTraceContext(spanId);
      return Brave.toSpan(toSpanId(tracer.joinSpan(context).context()));
    }
  }

  static final class Brave4Recorder extends Recorder {
    final Tracing tracing;
    final Tracer tracer;

    Brave4Recorder(Tracing tracing) {
      this.tracing = tracing;
      this.tracer = tracing.tracer();
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
        case "sa":
          brave4.kind(brave.Span.Kind.CLIENT);
          break;
        case "ca":
          brave4.kind(brave.Span.Kind.SERVER);
          break;
        default:
          throw new AssertionError(key + " is not yet supported");
      }

      zipkin2.Endpoint.Builder endpointBuilder =
          zipkin2.Endpoint.newBuilder()
              .serviceName(endpoint.service_name)
              .port(endpoint.port != null ? endpoint.port : 0);
      if (endpoint.ipv6 != null) {
        endpointBuilder.parseIp(endpoint.ipv6);
      }
      int ipv4 = endpoint.ipv4;
      if (endpoint.ipv4 != 0) {
        endpointBuilder.parseIp( // allocation is ok here as Endpoint.ipv4Bytes would anyway
            new byte[] {
              (byte) (ipv4 >> 24 & 0xff),
              (byte) (ipv4 >> 16 & 0xff),
              (byte) (ipv4 >> 8 & 0xff),
              (byte) (ipv4 & 0xff)
            });
      }
      brave4.remoteEndpoint(endpointBuilder.build());
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

    @Override
    long currentTimeMicroseconds(Span span) {
      return tracing
          .clock(toTraceContext(InternalSpan.instance.context(span)))
          .currentTimeMicroseconds();
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
