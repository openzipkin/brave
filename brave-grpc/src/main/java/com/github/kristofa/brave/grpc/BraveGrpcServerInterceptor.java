package com.github.kristofa.brave.grpc;

import static com.github.kristofa.brave.grpc.GrpcKeys.GRPC_STATUS_CODE;
import static com.google.common.base.Preconditions.checkNotNull;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.IdConversion;
import com.github.kristofa.brave.KeyValueAnnotation;
import com.github.kristofa.brave.ServerRequestAdapter;
import com.github.kristofa.brave.ServerRequestInterceptor;
import com.github.kristofa.brave.ServerResponseAdapter;
import com.github.kristofa.brave.ServerResponseInterceptor;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.TraceData;

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

    private final ServerRequestInterceptor serverRequestInterceptor;
    private final ServerResponseInterceptor serverResponseInterceptor;

    public BraveGrpcServerInterceptor(Brave brave) {
        this.serverRequestInterceptor = checkNotNull(brave.serverRequestInterceptor());
        this.serverResponseInterceptor = checkNotNull(brave.serverResponseInterceptor());
    }

    @Override
    public <ReqT, RespT> Listener<ReqT> interceptCall(final MethodDescriptor<ReqT, RespT> method,
                                                      final ServerCall<RespT> call, final Metadata requestHeaders,
                                                      final ServerCallHandler<ReqT, RespT> next) {
        return next.startCall(method, new SimpleForwardingServerCall<RespT>(call) {
            @Override
            public void request(int numMessages) {
                serverRequestInterceptor.handle(new GrpcServerRequestAdapter<>(method, requestHeaders));
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

        private MethodDescriptor<ReqT, RespT> method;
        private Metadata requestHeaders;

        public GrpcServerRequestAdapter(MethodDescriptor<ReqT, RespT> method, Metadata requestHeaders) {
            this.method = checkNotNull(method);
            this.requestHeaders = checkNotNull(requestHeaders);
        }

        @Override
        public TraceData getTraceData() {
            final String sampled = requestHeaders.get(BravePropagationKeys.Sampled);
            if (sampled != null) {
                if (sampled.equals("0") || sampled.toLowerCase().equals("false")) {
                    return TraceData.builder().sample(false).build();
                } else {
                    final String parentSpanId = requestHeaders.get(BravePropagationKeys.ParentSpanId);
                    final String traceId = requestHeaders.get(BravePropagationKeys.TraceId);
                    final String spanId = requestHeaders.get(BravePropagationKeys.SpanId);
                    if (traceId != null && spanId != null) {
                        SpanId span = getSpanId(traceId, spanId, parentSpanId);
                        return TraceData.builder().sample(true).spanId(span).build();
                    }
                }
            }
            return TraceData.builder().build();
        }

        @Override
        public String getSpanName() {
            return method.getFullMethodName().toLowerCase();
        }

        @Override
        public Collection<KeyValueAnnotation> requestAnnotations() {
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

    static SpanId getSpanId(String traceId, String spanId, String parentSpanId) {
        if (parentSpanId != null) {
            return SpanId.create(IdConversion.convertToLong(traceId), IdConversion.convertToLong(spanId), IdConversion.convertToLong(parentSpanId));
        }
        return SpanId.create(IdConversion.convertToLong(traceId), IdConversion.convertToLong(spanId), null);
    }
}
