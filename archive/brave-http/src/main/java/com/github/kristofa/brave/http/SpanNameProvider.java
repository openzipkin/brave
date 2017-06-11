package com.github.kristofa.brave.http;

/**
 * @deprecated Replaced by {@code HttpClientParser} or {@code HttpServerParser} from brave-http
 */
@Deprecated
public interface SpanNameProvider {

    String spanName(HttpRequest request);
}
