package com.github.kristofa.brave.grpc;

import static com.google.common.base.Preconditions.checkNotNull;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerRequestInterceptor;
import com.github.kristofa.brave.ServerResponseInterceptor;
import com.github.kristofa.brave.http.HttpServerRequest;
import com.github.kristofa.brave.http.HttpServerRequestAdapter;
import com.github.kristofa.brave.http.HttpServerResponseAdapter;

import io.grpc.ForwardingServerCall.SimpleForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import java.net.URI;

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
                serverRequestInterceptor.handle(new HttpServerRequestAdapter(
                    new GrpcHttpServerRequest<>(method, requestHeaders),
                    new MethodSpanNameProvider<>(method)));
                super.request(numMessages);
            }

            @Override
            public void close(Status status, Metadata trailers) {
                serverResponseInterceptor.handle(new HttpServerResponseAdapter(new GrpcHttpResponse(status)));
                super.close(status, trailers);
            }
        }, requestHeaders);
    }

    private static final class GrpcHttpServerRequest<ReqT, RespT> implements HttpServerRequest {

        private final MethodDescriptor<ReqT, RespT> method;
        private final Metadata requestHeaders;

        public GrpcHttpServerRequest(MethodDescriptor<ReqT, RespT> method, Metadata requestHeaders) {
            this.method = checkNotNull(method);
            this.requestHeaders = checkNotNull(requestHeaders);
        }

        @Override
        public String getHttpHeaderValue(String headerName) {
            Key<String> key = GrpcHeaders.HEADERS.get(headerName);
            return key != null ? requestHeaders.get(key) : null;
        }

        @Override
        public URI getUri() {
            return URI.create(method.getFullMethodName().toLowerCase());
        }

        @Override
        public String getHttpMethod() {
            return "POST";
        }

    }

}
