package com.github.kristofa.brave.http;

import com.twitter.zipkin.gen.Endpoint;

public class MissingClientRemoteEndpointExtractor implements ClientRemoteEndpointExtractor {
    @Override
    public Endpoint remoteEndpoint(HttpRequest request) {
        return null;
    }
}
