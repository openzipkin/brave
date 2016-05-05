package com.github.kristofa.brave.grpc;

import com.twitter.zipkin.gen.Endpoint;
import io.grpc.Channel;
import io.grpc.MethodDescriptor;

public class MissingClientRemoteEndpointExtractor implements ClientRemoteEndpointExtractor {
    @Override
    public Endpoint remoteEndpoint(Channel channel, MethodDescriptor<?, ?> method) {
        return null;
    }
}
