package brave.grpc;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import io.grpc.ForwardingServerCall.SimpleForwardingServerCall;
import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import zipkin.Constants;

// not exposed directly as implementation notably changes between versions 1.2 and 1.3
final class TracingServerInterceptor implements ServerInterceptor {
  final Tracer tracer;
  final TraceContext.Extractor<Metadata> extractor;

  TracingServerInterceptor(Tracing tracing) {
    tracer = tracing.tracer();
    extractor = tracing.propagationFactory().create(AsciiMetadataKeyFactory.INSTANCE)
        .extractor(new Propagation.Getter<Metadata, Metadata.Key<String>>() { // retrolambda no like
          @Override public String get(Metadata metadata, Metadata.Key<String> key) {
            return metadata.get(key);
          }
        });
  }

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(final ServerCall<ReqT, RespT> call,
      final Metadata requestHeaders, final ServerCallHandler<ReqT, RespT> next) {
    TraceContextOrSamplingFlags contextOrFlags = extractor.extract(requestHeaders);
    Span span = contextOrFlags.context() != null
        ? tracer.joinSpan(contextOrFlags.context())
        : tracer.newTrace(contextOrFlags.samplingFlags());
    span.kind(Span.Kind.SERVER).name(call.getMethodDescriptor().getFullMethodName());

    // startCall invokes user interceptors, so we place the span in scope here
    ServerCall.Listener<ReqT> result;
    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
      result = next.startCall(new TracingServerCall<>(span, call), requestHeaders);
    }

    // This ensures the server implementation can see the span in scope
    return new ScopingServerCallListener<>(tracer, span, result);
  }

  static final class TracingServerCall<ReqT, RespT>
      extends SimpleForwardingServerCall<ReqT, RespT> {
    private final Span span;

    TracingServerCall(Span span, ServerCall<ReqT, RespT> call) {
      super(call);
      this.span = span;
    }

    @Override public void request(int numMessages) {
      span.start();
      super.request(numMessages);
    }

    @Override public void close(Status status, Metadata trailers) {
      try {
        if (!status.getCode().equals(Status.Code.OK)) {
          span.tag(Constants.ERROR, String.valueOf(status.getCode()));
        }
        super.close(status, trailers);
      } finally {
        span.finish();
      }
    }
  }

  static final class ScopingServerCallListener<ReqT>
      extends SimpleForwardingServerCallListener<ReqT> {
    final Tracer tracer;
    final Span span;

    ScopingServerCallListener(Tracer tracer, Span span, ServerCall.Listener<ReqT> delegate) {
      super(delegate);
      this.tracer = tracer;
      this.span = span;
    }

    @Override public void onMessage(ReqT message) {
      try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
        delegate().onMessage(message);
      }
    }

    @Override public void onHalfClose() {
      try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
        delegate().onHalfClose();
      }
    }

    @Override public void onCancel() {
      try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
        delegate().onCancel();
      }
    }

    @Override public void onComplete() {
      try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
        delegate().onCancel();
      }
    }

    @Override public void onReady() {
      try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
        delegate().onReady();
      }
    }
  }
}
