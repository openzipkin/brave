package com.github.kristofa.brave.grpc;

import com.github.kristofa.brave.http.BraveHttpHeaders;

import io.grpc.Metadata;

class GrpcHeaders {

    public static Metadata.Key<String> ParentSpanId =
        Metadata.Key.of(BraveHttpHeaders.ParentSpanId.getName(), Metadata.ASCII_STRING_MARSHALLER);
    public static Metadata.Key<String> SpanId =
        Metadata.Key.of(BraveHttpHeaders.SpanId.getName(), Metadata.ASCII_STRING_MARSHALLER);
    public static Metadata.Key<String> TraceId =
        Metadata.Key.of(BraveHttpHeaders.TraceId.getName(), Metadata.ASCII_STRING_MARSHALLER);
    public static Metadata.Key<String> Sampled =
        Metadata.Key.of(BraveHttpHeaders.Sampled.getName(), Metadata.ASCII_STRING_MARSHALLER);

}
