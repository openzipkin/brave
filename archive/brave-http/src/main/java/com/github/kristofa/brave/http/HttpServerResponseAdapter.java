package com.github.kristofa.brave.http;

import java.util.Collection;
import java.util.Collections;
import com.github.kristofa.brave.KeyValueAnnotation;
import com.github.kristofa.brave.ServerResponseAdapter;
import zipkin.TraceKeys;

/**
 * @deprecated Replaced by {@code HttpServerParser} from brave-http
 */
@Deprecated
public class HttpServerResponseAdapter implements ServerResponseAdapter {

    private final HttpResponse response;

    public HttpServerResponseAdapter(HttpResponse response)
    {
        this.response = response;
    }

    @Override
    public Collection<KeyValueAnnotation> responseAnnotations() {
        return Collections.singleton(KeyValueAnnotation.create(
                TraceKeys.HTTP_STATUS_CODE, String.valueOf(response.getHttpStatusCode())));
    }
}
