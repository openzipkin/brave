package com.github.kristofa.brave.grpc;

import com.twitter.zipkin.gen.Endpoint;
import io.grpc.Channel;
import io.grpc.MethodDescriptor;

public interface ClientRemoteEndpointExtractor {
    Endpoint remoteEndpoint(Channel channel, MethodDescriptor<?, ?> method);
}
