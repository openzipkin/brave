package com.github.kristofa.brave.grpc;

import com.github.kristofa.brave.http.HttpRequest;
import com.github.kristofa.brave.http.SpanNameProvider;

import io.grpc.MethodDescriptor;

final class MethodSpanNameProvider<ReqT, RespT> implements SpanNameProvider {
    private final MethodDescriptor<ReqT, RespT> method;

    public MethodSpanNameProvider(MethodDescriptor<ReqT, RespT> method) {
        this.method = method;
    }

    @Override
    public String spanName(HttpRequest reqest) {
        return method.getFullMethodName().toLowerCase();
    }

}
