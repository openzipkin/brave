package brave.grpc;

import brave.SpanCustomizer;
import io.grpc.CallOptions;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

public class GrpcClientParser extends GrpcParser {
  /** Override the customize the span based on the start of a request. */
  protected <ReqT, RespT> void onStart(MethodDescriptor<ReqT, RespT> method, CallOptions options,
      Metadata headers, SpanCustomizer span) {
    span.name(spanName(method));
  }
}
