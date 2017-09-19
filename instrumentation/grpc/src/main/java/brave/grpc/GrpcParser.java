package brave.grpc;

import brave.SpanCustomizer;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

public class GrpcParser {

  /** Returns the span name of the request. Defaults to the full grpc method name. */
  protected <ReqT, RespT> String spanName(MethodDescriptor<ReqT, RespT> methodDescriptor) {
    return methodDescriptor.getFullMethodName();
  }

  /** Override to customize the span based on a message sent to the peer. */
  protected <M> void onMessageSent(M message, SpanCustomizer span) {
  }

  /** Override to customize the span based on the message received from the peer. */
  protected <M> void onMessageReceived(M message, SpanCustomizer span) {
  }

  /**
   * Override to change what data from the status or trailers are parsed into the span modeling it.
   * By default, this tags "grpc.status_code" and "error" when it is not OK.
   */
  protected void onClose(Status status, Metadata trailers, SpanCustomizer span) {
    if (status != null && status.getCode() != Status.Code.OK) {
      String code = String.valueOf(status.getCode());
      span.tag("grpc.status_code", code);
      span.tag("error", code);
    }
  }

  /**
   * This is used when there is an unexpected error present in the operation.
   *
   * <p>Conventionally associated with the tag key "error"
   */
  protected void onError(Throwable error, SpanCustomizer span) {
    String message = error.getMessage();
    if (message == null) message = error.getClass().getSimpleName();
    span.tag("error", message);
  }
}
