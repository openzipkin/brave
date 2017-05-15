package com.github.kristofa.brave.http;

/**
 * @deprecated Replaced by {@code HttpClientAdapter} from brave-http
 */
@Deprecated
public interface HttpClientRequest extends HttpRequest {

    /**
     * Adds headers to request.
     *
     * @param header header name.
     * @param value header value.
     */
    void addHeader(String header, String value);

}
