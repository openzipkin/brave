package brave.grpc;

import brave.ServerHandler;
import brave.Span;
import brave.Tracer;
import brave.parser.Parser;
import brave.parser.TagsParser;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

public class BraveGrpcServerInterceptor implements ServerInterceptor {

  public static class Config extends ServerHandler.Config<ServerCall, Status> {

    @Override protected Parser<ServerCall, String> spanNameParser() {
      return c -> c.getMethodDescriptor().getFullMethodName();
    }

    @Override protected TagsParser<ServerCall> requestTagsParser() {
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
  ServerHandler<ServerCall, Status> serverHandler = ServerHandler.create(new Config());
  TraceContext.Extractor<Metadata> contextExtractor =
      Propagation.Factory.B3.create(AsciiMetadataKeyFactory.INSTANCE).extractor(Metadata::get);

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(final ServerCall<ReqT, RespT> call,
      final Metadata requestHeaders,
      final ServerCallHandler<ReqT, RespT> next) {
    return next.startCall(new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
      @Override
      public void request(int numMessages) {
        TraceContextOrSamplingFlags contextOrFlags = contextExtractor.extract(requestHeaders);
        Span span = contextOrFlags.context() != null
            ? tracer.joinSpan(contextOrFlags.context())
            : tracer.newTrace(contextOrFlags.samplingFlags());
        try {
          serverHandler.handleReceive(call, span);
          // TODO: attach span to current context probably with grpc metadata
        } catch (RuntimeException e) {
          throw serverHandler.handleError(e, span);
        }
        super.request(numMessages);
      }

      @Override
      public void close(Status status, Metadata trailers) {
        // TODO: get the span from the response
        Span span = null;//(Span) response.getAttribute(ServerHandler.CONTEXT_KEY);
        serverHandler.handleSend(status, span);
        super.close(status, trailers);
      }
    }, requestHeaders);
  }
}