package brave.grpc;

import brave.Span;
import brave.Tracer;
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

// not exposed directly as implementation notably changes between versions 1.2 and 1.3
final class TracingServerInterceptor implements ServerInterceptor {
  final Tracer tracer;
  final TraceContext.Extractor<Metadata> extractor;
  final GrpcServerParser parser;

  TracingServerInterceptor(GrpcTracing grpcTracing) {
    tracer = grpcTracing.tracing().tracer();
    extractor = grpcTracing.tracing().propagationFactory().create(AsciiMetadataKeyFactory.INSTANCE)
        .extractor(new Propagation.Getter<Metadata, Metadata.Key<String>>() { // retrolambda no like
          @Override public String get(Metadata metadata, Metadata.Key<String> key) {
            return metadata.get(key);
          }
        });
    parser = grpcTracing.serverParser();
  }

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(final ServerCall<ReqT, RespT> call,
      final Metadata headers, final ServerCallHandler<ReqT, RespT> next) {
    TraceContextOrSamplingFlags contextOrFlags = extractor.extract(headers);
    Span span = contextOrFlags.context() != null
        ? tracer.joinSpan(contextOrFlags.context())
        : tracer.newTrace(contextOrFlags.samplingFlags());
    span.kind(Span.Kind.SERVER);
    parser.onStart(call, headers, span);
    // startCall invokes user interceptors, so we place the span in scope here
    ServerCall.Listener<ReqT> result;
    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
      result = next.startCall(new TracingServerCall<>(span, call, parser), headers);
    } catch (RuntimeException | Error e) {
      parser.onError(e, span);
      span.finish();
      throw e;
    }

    // This ensures the server implementation can see the span in scope
    return new ScopingServerCallListener<>(tracer, span, result, parser);
  }

  static final class TracingServerCall<ReqT, RespT>
      extends SimpleForwardingServerCall<ReqT, RespT> {
    final Span span;
    final GrpcServerParser parser;

    TracingServerCall(Span span, ServerCall<ReqT, RespT> call, GrpcServerParser parser) {
      super(call);
      this.span = span;
      this.parser = parser;
    }

    @Override public void request(int numMessages) {
      span.start();
      super.request(numMessages);
    }

    @Override
    public void sendMessage(RespT message) {
      super.sendMessage(message);
      parser.onMessageSent(message, span);
    }

    @Override public void close(Status status, Metadata trailers) {
      try {
        super.close(status, trailers);
        parser.onClose(status, trailers, span);
      } catch (RuntimeException | Error e) {
        parser.onError(e, span);
        throw e;
      } finally {
        span.finish();
      }
    }
  }

  static final class ScopingServerCallListener<ReqT>
      extends SimpleForwardingServerCallListener<ReqT> {
    final Tracer tracer;
    final Span span;
    final GrpcServerParser parser;

    ScopingServerCallListener(Tracer tracer, Span span, ServerCall.Listener<ReqT> delegate,
        GrpcServerParser parser) {
      super(delegate);
      this.tracer = tracer;
      this.span = span;
      this.parser = parser;
    }

    @Override public void onMessage(ReqT message) {
      try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
        parser.onMessageReceived(message, span);
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
        delegate().onComplete();
      }
    }

    @Override public void onReady() {
      try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
        delegate().onReady();
      }
    }
  }
}
