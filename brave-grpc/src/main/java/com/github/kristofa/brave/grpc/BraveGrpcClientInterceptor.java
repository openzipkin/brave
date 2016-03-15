package com.github.kristofa.brave.grpc;

import static com.google.common.base.Preconditions.checkNotNull;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.ClientResponseInterceptor;
import com.github.kristofa.brave.ClientSpanThreadBinder;
import com.github.kristofa.brave.http.HttpClientRequest;
import com.github.kristofa.brave.http.HttpClientRequestAdapter;
import com.github.kristofa.brave.http.HttpClientResponseAdapter;
import com.github.kristofa.brave.http.StringServiceNameProvider;
import com.google.common.base.Strings;
import com.twitter.zipkin.gen.Span;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

import java.net.URI;

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
                clientRequestInterceptor.handle(new HttpClientRequestAdapter(
                    new GrpcHttpClientRequest<>(method, headers), new StringServiceNameProvider(clientService),
                        new MethodSpanNameProvider<>(method)));
                final Span currentClientSpan = clientSpanThreadBinder.getCurrentClientSpan();
                super.start(new SimpleForwardingClientCallListener<RespT>(responseListener) {
                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        try {
                            clientSpanThreadBinder.setCurrentSpan(currentClientSpan);
                            clientResponseInterceptor.handle(new HttpClientResponseAdapter(new GrpcHttpResponse(status)));
                            super.onClose(status, trailers);
                        } finally {
                            clientSpanThreadBinder.setCurrentSpan(null);
                        }
                   }
                }, headers);
            }
        };

    }

    private static final class GrpcHttpClientRequest<ReqT, RespT> implements HttpClientRequest {

        private final MethodDescriptor<ReqT, RespT> method;
        private Metadata headers;

        public GrpcHttpClientRequest(MethodDescriptor<ReqT, RespT> method, Metadata headers) {
            this.method = checkNotNull(method);
            this.headers = checkNotNull(headers);
        }

        @Override
        public URI getUri() {
            return URI.create(method.getFullMethodName().toLowerCase());
        }

        @Override
        public String getHttpMethod() {
            return "POST";
        }

        @Override
        public void addHeader(String header, String value) {
            Key<String> key = GrpcHeaders.HEADERS.get(header);
            if (key != null) {
                headers.put(key, value);
            }
        }
    }

}
