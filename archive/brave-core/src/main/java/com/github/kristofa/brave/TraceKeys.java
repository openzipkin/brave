package com.github.kristofa.brave;

/**
 * @deprecated use {@link zipkin.TraceKeys}; will be removed in Brave 2.0
 */
@Deprecated
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
