package brave.grpc;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.Propagation.Setter;
import brave.propagation.TraceContext;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

// not exposed directly as implementation notably changes between versions 1.2 and 1.3
final class TracingClientInterceptor implements ClientInterceptor {
  static final Setter<Metadata, Metadata.Key<String>> SETTER =
      new Setter<Metadata, Metadata.Key<String>>() { // retrolambda no like
        @Override public void put(Metadata metadata, Metadata.Key<String> key, String value) {
          metadata.removeAll(key);
          if (value != null) metadata.put(key, value);
        }
      };

  final Tracer tracer;
  final TraceContext.Injector<Metadata> injector;

  TracingClientInterceptor(Tracing tracing) {
    tracer = tracing.tracer();
    injector =
        tracing.propagationFactory().create(AsciiMetadataKeyFactory.INSTANCE).injector(SETTER);
  }

  /**
   * This sets as span in scope both for the interception and for the start of the request. It does
   * not set a span in scope during the response listener as it is unexpected it would be used at
   * that fine granularity. If users want access to the span in a response listener, they will need
   * to wrap the executor with one that's aware of the current context.
   */
  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      final MethodDescriptor<ReqT, RespT> method, final CallOptions callOptions,
      final Channel next) {
    Span span = tracer.nextSpan();
    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
      return new SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
        @Override
        public void start(Listener<RespT> responseListener, Metadata headers) {
          injector.inject(span.context(), headers);
          span.kind(Span.Kind.CLIENT).name(method.getFullMethodName()).start();
          try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
            super.start(new SimpleForwardingClientCallListener<RespT>(responseListener) {
              @Override public void onClose(Status status, Metadata trailers) {
                if (!status.getCode().equals(Status.Code.OK)) {
                  span.tag("error", String.valueOf(status.getCode()));
                }
                span.finish();
                super.onClose(status, trailers);
              }
            }, headers);
          }
        }
      };
    }
  }
}
