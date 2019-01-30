package brave.grpc;

import brave.Span;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import brave.grpc.GrpcPropagation.Tags;
import brave.propagation.Propagation;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContextOrSamplingFlags;
import io.grpc.ForwardingServerCall.SimpleForwardingServerCall;
import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import static brave.grpc.GrpcPropagation.RPC_METHOD;

// not exposed directly as implementation notably changes between versions 1.2 and 1.3
final class TracingServerInterceptor implements ServerInterceptor {
  static final Propagation.Getter<Metadata, Key<String>> GETTER =
      new Propagation.Getter<Metadata, Key<String>>() { // retrolambda no like
        @Override public String get(Metadata metadata, Key<String> key) {
          return metadata.get(key);
        }

        @Override public String toString() {
          return "Metadata::get";
        }
      };

  final Tracer tracer;
  final Extractor<Metadata> extractor;
  final GrpcServerParser parser;
  final boolean grpcPropagationFormatEnabled;

  TracingServerInterceptor(GrpcTracing grpcTracing) {
    tracer = grpcTracing.tracing.tracer();
    extractor = grpcTracing.propagation.extractor(GETTER);
    parser = grpcTracing.serverParser;
    grpcPropagationFormatEnabled = grpcTracing.grpcPropagationFormatEnabled;
  }

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(final ServerCall<ReqT, RespT> call,
      final Metadata headers, final ServerCallHandler<ReqT, RespT> next) {
    TraceContextOrSamplingFlags extracted = extractor.extract(headers);
    Span span = extracted.context() != null
        ? tracer.joinSpan(extracted.context())
        : tracer.nextSpan(extracted);

    // If grpc propagation is enabled, make sure we refresh the server method
    if (grpcPropagationFormatEnabled) {
      Tags tags = span.context().findExtra(Tags.class);
      if (tags != null) tags.put(RPC_METHOD, call.getMethodDescriptor().getFullMethodName());
    }

    span.kind(Span.Kind.SERVER);
    parser.onStart(call, headers, span.customizer());
    // startCall invokes user interceptors, so we place the span in scope here
    ServerCall.Listener<ReqT> result;
    SpanInScope scope = tracer.withSpanInScope(span);
    try { // retrolambda can't resolve this try/finally
      result = next.startCall(new TracingServerCall<>(span, call, parser), headers);
    } catch (RuntimeException | Error e) {
      span.error(e);
      span.finish();
      throw e;
    } finally {
      scope.close();
    }

    // This ensures the server implementation can see the span in scope
    return new ScopingServerCallListener<>(tracer, span, result, parser);
  }

  final class TracingServerCall<ReqT, RespT> extends SimpleForwardingServerCall<ReqT, RespT> {
    final Span span;

    TracingServerCall(Span span, ServerCall<ReqT, RespT> call, GrpcServerParser parser) {
      super(call);
      this.span = span;
    }

    @Override public void request(int numMessages) {
      span.start();
      super.request(numMessages);
    }

    @Override
    public void sendMessage(RespT message) {
      super.sendMessage(message);
      parser.onMessageSent(message, span.customizer());
    }

    @Override public void close(Status status, Metadata trailers) {
      try {
        super.close(status, trailers);
        parser.onClose(status, trailers, span.customizer());
      } catch (RuntimeException | Error e) {
        span.error(e);
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
      SpanInScope scope = tracer.withSpanInScope(span);
      try { // retrolambda can't resolve this try/finally
        parser.onMessageReceived(message, span.customizer());
        delegate().onMessage(message);
      } finally {
        scope.close();
      }
    }

    @Override public void onHalfClose() {
      SpanInScope scope = tracer.withSpanInScope(span);
      try { // retrolambda can't resolve this try/finally
        delegate().onHalfClose();
      } catch (RuntimeException | Error e) {
        // If there was an exception executing onHalfClose, we don't expect other lifecycle
        // commands to succeed. Accordingly, we close the span
        span.error(e);
        span.finish();
        throw e;
      } finally {
        scope.close();
      }
    }

    @Override public void onCancel() {
      SpanInScope scope = tracer.withSpanInScope(span);
      try { // retrolambda can't resolve this try/finally
        delegate().onCancel();
      } finally {
        scope.close();
      }
    }

    @Override public void onComplete() {
      SpanInScope scope = tracer.withSpanInScope(span);
      try { // retrolambda can't resolve this try/finally
        delegate().onComplete();
      } finally {
        scope.close();
      }
    }

    @Override public void onReady() {
      SpanInScope scope = tracer.withSpanInScope(span);
      try { // retrolambda can't resolve this try/finally
        delegate().onReady();
      } finally {
        scope.close();
      }
    }
  }
}
