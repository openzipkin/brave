package com.github.kristofa.brave;

import com.google.common.base.Optional;

import java.net.URI;

public interface ClientRequestAdapter {
    URI getUri();

    String getMethod();

    Optional<String> getSpanName();

    void addHeader(String header, String value);
}
