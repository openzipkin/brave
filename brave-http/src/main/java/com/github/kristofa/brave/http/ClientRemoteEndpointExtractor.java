package com.github.kristofa.brave.http;

import com.twitter.zipkin.gen.Endpoint;

public interface ClientRemoteEndpointExtractor {
    Endpoint remoteEndpoint(HttpRequest request);
}
