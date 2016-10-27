package com.github.kristofa.brave.grpc;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientRequestAdapter;
import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.ClientResponseAdapter;
import com.github.kristofa.brave.ClientResponseInterceptor;
import com.github.kristofa.brave.ClientSpanThreadBinder;
import com.github.kristofa.brave.IdConversion;
import com.github.kristofa.brave.KeyValueAnnotation;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.internal.Nullable;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.Status.Code;

import java.util.Collection;
import java.util.Collections;

import static com.github.kristofa.brave.grpc.GrpcKeys.GRPC_STATUS_CODE;
import static com.google.common.base.Preconditions.checkNotNull;

public final class BraveGrpcClientInterceptor implements ClientInterceptor {

    private final ClientRequestInterceptor clientRequestInterceptor;
    private final ClientResponseInterceptor clientResponseInterceptor;
    private final ClientSpanThreadBinder clientSpanThreadBinder;

    public BraveGrpcClientInterceptor(Brave brave) {
        this.clientRequestInterceptor = checkNotNull(brave.clientRequestInterceptor());
        this.clientResponseInterceptor = checkNotNull(brave.clientResponseInterceptor());
        this.clientSpanThreadBinder = checkNotNull(brave.clientSpanThreadBinder());
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(final MethodDescriptor<ReqT, RespT> method,
                                                               final CallOptions callOptions, final Channel next) {
        return new SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                clientRequestInterceptor.handle(new GrpcClientRequestAdapter<>(method, headers));
                final Span currentClientSpan = clientSpanThreadBinder.getCurrentClientSpan();
                super.start(new SimpleForwardingClientCallListener<RespT>(responseListener) {
                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        try {
                            clientSpanThreadBinder.setCurrentSpan(currentClientSpan);
                            clientResponseInterceptor.handle(new GrpcClientResponseAdapter(status));
                            super.onClose(status, trailers);
                        } finally {
                            clientSpanThreadBinder.setCurrentSpan(null);
                        }
                   }
                }, headers);
            }
        };

    }

    static final class GrpcClientRequestAdapter<ReqT, RespT> implements ClientRequestAdapter {

        private final MethodDescriptor<ReqT, RespT> method;
        private final Metadata headers;

        public GrpcClientRequestAdapter(MethodDescriptor<ReqT, RespT> method, Metadata headers) {
            this.method = checkNotNull(method);
            this.headers = checkNotNull(headers);
        }

        @Override
        public String getSpanName() {
            return method.getFullMethodName().toLowerCase();
        }

        @Override
        public void addSpanIdToRequest(@Nullable SpanId spanId) {
            if (spanId == null) {
                headers.put(BravePropagationKeys.Sampled, "0");
            } else {
                headers.put(BravePropagationKeys.Sampled, "1");
                headers.put(BravePropagationKeys.TraceId, spanId.traceIdString());
                headers.put(BravePropagationKeys.SpanId, IdConversion.convertToString(spanId.spanId));
                if (spanId.nullableParentId() != null) {
                    headers.put(BravePropagationKeys.ParentSpanId, IdConversion.convertToString(spanId.parentId));
                }
            }
        }

        @Override
        public Collection<KeyValueAnnotation> requestAnnotations() {
            return Collections.emptyList();
        }

        @Override
        public Endpoint serverAddress() {
            return null;
        }
    }

    static final class GrpcClientResponseAdapter implements ClientResponseAdapter {

        private final Status status;

        public GrpcClientResponseAdapter(Status status) {
            this.status = checkNotNull(status);
        }

        @Override
        public Collection<KeyValueAnnotation> responseAnnotations() {
            Code statusCode = status.getCode();
            return statusCode == Code.OK
                ? Collections.<KeyValueAnnotation>emptyList()
                : Collections.singletonList(KeyValueAnnotation.create(GRPC_STATUS_CODE, statusCode.name()));
        }
    }
}
