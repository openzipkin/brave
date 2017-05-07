package brave.grpc;

import brave.Tracing;
import io.grpc.ClientInterceptor;
import io.grpc.ServerInterceptor;

public final class GrpcTracing {

  public static GrpcTracing create(Tracing tracing) {
    return new GrpcTracing(tracing);
  }

  final Tracing tracing;

  GrpcTracing(Tracing tracing) { // intentionally hidden
    if (tracing == null) throw new NullPointerException("tracing == null");
    this.tracing = tracing;
  }

  /** This interceptor traces outbound calls */
  public ClientInterceptor newClientInterceptor() {
    return new TracingClientInterceptor(tracing);
  }

  /** This interceptor traces inbound calls */
  public ServerInterceptor newServerInterceptor() {
    return new TracingServerInterceptor(tracing);
  }
}
