package com.github.kristofa.brave.http;

/**
 * @deprecated Replaced by {@code HttpClientAdapter} or {@code HttpServerAdapter} from brave-http
 */
@Deprecated
public interface HttpResponse {

    int getHttpStatusCode();
}
