package brave.grpc;

import brave.ClientHandler;
import brave.Span;
import brave.Tracer;
import brave.parser.Parser;
import brave.parser.TagsParser;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

public class BraveGrpcClientInterceptor implements ClientInterceptor {

  public static class Config extends ClientHandler.Config<MethodDescriptor, Status> {

    @Override protected Parser<MethodDescriptor, String> spanNameParser() {
      return MethodDescriptor::getFullMethodName;
    }

    @Override protected TagsParser<MethodDescriptor> requestTagsParser() {
      return (req, span) -> {
      };
    }

    @Override protected TagsParser<Status> responseTagsParser() {
      return (status, span) -> {
        if (!status.getCode().equals(Status.Code.OK)) {
          span.tag("grpc.status_code", String.valueOf(status.getCode()));
        }
      };
    }
  }

  // all this still will be set via builder
  Tracer tracer = Tracer.newBuilder().build(); // add reporter etc
  ClientHandler<MethodDescriptor, Status> clientHandler = ClientHandler.create(new Config());
  TraceContext.Injector<Metadata> injector =
      Propagation.Factory.B3.create(AsciiMetadataKeyFactory.INSTANCE).injector(Metadata::put);

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      final MethodDescriptor<ReqT, RespT> method,
      final CallOptions callOptions, final Channel next) {
    return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
        next.newCall(method, callOptions)) {

      @Override
      public void start(Listener<RespT> responseListener, Metadata headers) {
        // TODO: get current span
        Span span = tracer.newTrace();
        clientHandler.handleSend(method, span);
        injector.inject(span.context(), headers);
        super.start(new SimpleForwardingClientCallListener<RespT>(responseListener) {
          @Override
          public void onClose(Status status, Metadata trailers) {
            clientHandler.handleReceive(status, span);
            super.onClose(status, trailers);
          }
        }, headers);
      }
    };
  }
}