package brave.grpc;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import io.grpc.ForwardingServerCall;
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
    return next.startCall(new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
      @Override
      public void request(int numMessages) {
        try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
          span.kind(Span.Kind.SERVER).name(call.getMethodDescriptor().getFullMethodName()).start();
          super.request(numMessages);
        }
      }

      @Override
      public void close(Status status, Metadata trailers) {
        try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
          if (!status.getCode().equals(Status.Code.OK)) {
            span.tag(Constants.ERROR, String.valueOf(status.getCode()));
          }
          super.close(status, trailers);
        } finally {
          span.finish();
        }
      }
    }, requestHeaders);
  }
}
