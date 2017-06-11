package com.github.kristofa.brave.http;


/**
 * @deprecated Replaced by {@code HttpServerAdapter} from brave-http
 */
@Deprecated
public interface HttpServerRequest extends HttpRequest {

    /**
     * Get http header value.
     *
     * @param headerName
     * @return
     */
    String getHttpHeaderValue(String headerName);

}
