package com.github.kristofa.brave.grpc;

import static com.github.kristofa.brave.IdConversion.convertToLong;
import static com.github.kristofa.brave.grpc.GrpcKeys.GRPC_STATUS_CODE;
import static com.google.common.base.Preconditions.checkNotNull;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.KeyValueAnnotation;
import com.github.kristofa.brave.ServerRequestAdapter;
import com.github.kristofa.brave.ServerRequestInterceptor;
import com.github.kristofa.brave.ServerResponseAdapter;
import com.github.kristofa.brave.ServerResponseInterceptor;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.TraceData;

import com.github.kristofa.brave.internal.Util;
import io.grpc.ForwardingServerCall.SimpleForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.Status.Code;

import java.util.Collection;
import java.util.Collections;

public final class BraveGrpcServerInterceptor implements ServerInterceptor {

    /** Creates a tracing interceptor with defaults. Use {@link #builder(Brave)} to customize. */
    public static BraveGrpcServerInterceptor create(Brave brave) {
        return new Builder(brave).build();
    }

    public static Builder builder(Brave brave) {
        return new Builder(brave);
    }

    public static final class Builder {
        final Brave brave;

        Builder(Brave brave) { // intentionally hidden
            this.brave = Util.checkNotNull(brave, "brave");
        }

        public BraveGrpcServerInterceptor build() {
            return new BraveGrpcServerInterceptor(this);
        }
    }

    private final ServerRequestInterceptor serverRequestInterceptor;
    private final ServerResponseInterceptor serverResponseInterceptor;

    BraveGrpcServerInterceptor(Builder b) { // intentionally hidden
        this.serverRequestInterceptor = b.brave.serverRequestInterceptor();
        this.serverResponseInterceptor = b.brave.serverResponseInterceptor();
    }

    /**
     * @deprecated please use {@link #create(Brave)} or {@link #builder(Brave)}
     */
    @Deprecated
    public BraveGrpcServerInterceptor(Brave brave) {
        this(builder(brave));
    }

    @Override
    public <ReqT, RespT> Listener<ReqT> interceptCall(final ServerCall<ReqT, RespT> call, final Metadata requestHeaders,
                                                      final ServerCallHandler<ReqT, RespT> next) {
        return next.startCall(new SimpleForwardingServerCall<ReqT, RespT>(call) {
            @Override
            public void request(int numMessages) {
                serverRequestInterceptor.handle(new GrpcServerRequestAdapter<>(call, requestHeaders));
                super.request(numMessages);
            }

            @Override
            public void close(Status status, Metadata trailers) {
                serverResponseInterceptor.handle(new GrpcServerResponseAdapter(status));
                super.close(status, trailers);
            }
        }, requestHeaders);
    }

    static final class GrpcServerRequestAdapter<ReqT, RespT> implements ServerRequestAdapter {

        private final ServerCall<ReqT, RespT> call;
        private final MethodDescriptor<ReqT, RespT> method;
        private final Metadata requestHeaders;

        GrpcServerRequestAdapter(ServerCall<ReqT, RespT> call, Metadata requestHeaders) {
            this.call = checkNotNull(call);
            this.method = checkNotNull(call.getMethodDescriptor());
            this.requestHeaders = checkNotNull(requestHeaders);
        }

        @Override
        public TraceData getTraceData() {
            String sampled = requestHeaders.get(BravePropagationKeys.Sampled);
            String parentSpanId = requestHeaders.get(BravePropagationKeys.ParentSpanId);
            String traceId = requestHeaders.get(BravePropagationKeys.TraceId);
            String spanId = requestHeaders.get(BravePropagationKeys.SpanId);

            // Official sampled value is 1, though some old instrumentation send true
            Boolean parsedSampled = sampled != null
                ? sampled.equals("1") || sampled.equalsIgnoreCase("true")
                : null;

            if (traceId != null && spanId != null) {
                return TraceData.create(getSpanId(traceId, spanId, parentSpanId, parsedSampled));
            } else if (parsedSampled == null) {
                return TraceData.EMPTY;
            } else if (parsedSampled.booleanValue()) {
                // Invalid: The caller requests the trace to be sampled, but didn't pass IDs
                return TraceData.EMPTY;
            } else {
                return TraceData.NOT_SAMPLED;
            }
        }

        @Override
        public String getSpanName() {
            return method.getFullMethodName().toLowerCase();
        }

        @Override
        public Collection<KeyValueAnnotation> requestAnnotations() {
            // the remote-addr attribute was removed in grpc 1.2
            return Collections.emptyList();
        }
    }

    static final class GrpcServerResponseAdapter implements ServerResponseAdapter {

        final Status status;

        public GrpcServerResponseAdapter(Status status) {
            this.status = status;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Collection<KeyValueAnnotation> responseAnnotations() {
            Code statusCode = status.getCode();
            return statusCode == Code.OK
                ? Collections.<KeyValueAnnotation>emptyList()
                : Collections.singletonList(KeyValueAnnotation.create(GRPC_STATUS_CODE, statusCode.name()));
        }

    }

    static SpanId getSpanId(String traceId, String spanId, String parentSpanId, Boolean sampled) {
        return SpanId.builder()
            .traceIdHigh(traceId.length() == 32 ? convertToLong(traceId, 0) : 0)
            .traceId(convertToLong(traceId))
            .spanId(convertToLong(spanId))
            .sampled(sampled)
            .parentId(parentSpanId == null ? null : convertToLong(parentSpanId)).build();
    }
}
