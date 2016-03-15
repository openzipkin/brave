package com.github.kristofa.brave.grpc;

import static com.google.common.base.Preconditions.checkNotNull;

import com.github.kristofa.brave.http.HttpResponse;

import io.grpc.Status;

final class GrpcHttpResponse implements HttpResponse {
    private final Status status;

    public GrpcHttpResponse(Status status) {
        this.status = checkNotNull(status);
    }

    @Override
    public int getHttpStatusCode() {
        return status.getCode().value();
    }
}
