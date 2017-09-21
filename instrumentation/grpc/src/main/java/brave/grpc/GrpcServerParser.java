package brave.grpc;

import brave.SpanCustomizer;
import io.grpc.Metadata;
import io.grpc.ServerCall;

public class GrpcServerParser extends GrpcParser {
  /** Override the customize the span based on the start of a request. */
  protected <ReqT, RespT> void onStart(ServerCall<ReqT, RespT> call, Metadata headers,
      SpanCustomizer span) {
    span.name(spanName(call.getMethodDescriptor()));
  }
}
