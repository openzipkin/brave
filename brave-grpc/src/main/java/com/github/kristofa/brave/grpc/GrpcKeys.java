package com.github.kristofa.brave.grpc;

import com.twitter.zipkin.gen.BinaryAnnotation;
import io.grpc.Status;

/** Well-known {@link BinaryAnnotation#key binary annotation keys} for gRPC */
public final class GrpcKeys {

    /**
     * The {@link Status.Code}, when not "OK". Ex. "CANCELLED"
     *
     * <p>Used to filter for error status.
     */
    public static final String GRPC_STATUS_CODE = "grpc.status_code";

    /**
     * The remote address of the client
     */
    public static final String GRPC_REMOTE_ADDR = "grpc.remote_addr";

    private GrpcKeys() {
    }
}
