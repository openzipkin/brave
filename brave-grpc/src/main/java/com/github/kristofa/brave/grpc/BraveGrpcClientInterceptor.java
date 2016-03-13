package com.github.kristofa.brave.grpc;

import static com.google.common.base.Preconditions.checkNotNull;

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
import com.google.common.base.Strings;
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

public final class BraveGrpcClientInterceptor implements ClientInterceptor {

    private final String clientService;
    private final ClientRequestInterceptor clientRequestInterceptor;
    private final ClientResponseInterceptor clientResponseInterceptor;
    private final ClientSpanThreadBinder clientSpanThreadBinder;

    public BraveGrpcClientInterceptor(String clientService, Brave brave) {
        if (Strings.isNullOrEmpty(clientService)) {
            throw new IllegalStateException("clientService name cannot be empty");
        }
        this.clientService = clientService;
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
                clientRequestInterceptor.handle(new GrpcClientRequestAdapter<>(clientService, method, headers));
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

    private static final class GrpcClientRequestAdapter<ReqT, RespT> implements ClientRequestAdapter {

        private final String clientService;
        private final MethodDescriptor<ReqT, RespT> method;
        private final Metadata headers;

        public GrpcClientRequestAdapter(String clientService, MethodDescriptor<ReqT, RespT> method, Metadata headers) {
            this.clientService = clientService;
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
                headers.put(GrpcHeaders.Sampled, "0");
            } else {
                headers.put(GrpcHeaders.Sampled, "1");
                headers.put(GrpcHeaders.TraceId, IdConversion.convertToString(spanId.getTraceId()));
                headers.put(GrpcHeaders.SpanId, IdConversion.convertToString(spanId.getSpanId()));
                if (spanId.getParentSpanId() != null) {
                    headers.put(GrpcHeaders.ParentSpanId, IdConversion.convertToString(spanId.getParentSpanId()));
                }
            }
        }

        @Override
        public String getClientServiceName() {
            return clientService;
        }

        @Override
        public Collection<KeyValueAnnotation> requestAnnotations() {
            return Collections.emptyList();
        }

    }

    private static final class GrpcClientResponseAdapter implements ClientResponseAdapter {

        private final Status status;

        public GrpcClientResponseAdapter(Status status) {
            this.status = checkNotNull(status);
        }

        @Override
        public Collection<KeyValueAnnotation> responseAnnotations() {
            Code statusCode = status.getCode();
            KeyValueAnnotation statusAnnotation = KeyValueAnnotation.create("grpc.statuscode", statusCode.name());
            return Collections.singletonList(statusAnnotation);
        }

    }

}
