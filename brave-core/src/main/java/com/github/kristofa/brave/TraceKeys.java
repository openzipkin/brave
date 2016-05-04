package com.github.kristofa.brave;

/**
 * Used for Consistent keys without taking in zipkin-dependency.
 * Taken from: https://github.com/openzipkin/zipkin-java/blob/master/zipkin/src/main/java/zipkin/TraceKeys.java
 */
public final class TraceKeys {

    public static final String HTTP_HOST = "http.host";

    public static final String HTTP_METHOD = "http.method";

    public static final String HTTP_PATH = "http.path";

    public static final String HTTP_URL = "http.url";

    public static final String HTTP_STATUS_CODE = "http.status_code";

    public static final String HTTP_REQUEST_SIZE = "http.request.size";

    public static final String HTTP_RESPONSE_SIZE = "http.response.size";

    private TraceKeys() {
    }
}
