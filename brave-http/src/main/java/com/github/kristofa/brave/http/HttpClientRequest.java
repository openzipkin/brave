package com.github.kristofa.brave.http;

import com.github.kristofa.brave.Propagation;

public interface HttpClientRequest extends HttpRequest {

    /**
     * @deprecated replaced by {@link Propagation.Setter}
     */
    @Deprecated
    void addHeader(String header, String value);

}
