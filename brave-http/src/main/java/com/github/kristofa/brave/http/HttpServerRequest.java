package com.github.kristofa.brave.http;

import com.github.kristofa.brave.Propagation;

public interface HttpServerRequest extends HttpRequest {

    /**
     * @deprecated replaced by {@link Propagation.Getter}
     */
    @Deprecated
    String getHttpHeaderValue(String headerName);

}
