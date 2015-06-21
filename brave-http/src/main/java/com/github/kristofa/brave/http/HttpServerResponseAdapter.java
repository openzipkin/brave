package com.github.kristofa.brave.http;

import com.github.kristofa.brave.KeyValueAnnotation;
import com.github.kristofa.brave.ServerResponseAdapter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class HttpServerResponseAdapter implements ServerResponseAdapter {

    private final HttpServerResponse response;

    public HttpServerResponseAdapter(HttpServerResponse response)
    {
        this.response = response;
    }

    @Override
    public Collection<KeyValueAnnotation> responseAnnotations() {
        int httpStatus = response.getHttpStatusCode();

        if ((httpStatus < 200) || (httpStatus > 299)) {
            KeyValueAnnotation statusAnnotation = KeyValueAnnotation.create("http.responsecode", String.valueOf(httpStatus));
            return Arrays.asList(statusAnnotation);
        }
        return Collections.EMPTY_LIST;
    }
}
