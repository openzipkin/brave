package com.github.kristofa.brave.http;

import com.github.kristofa.brave.ClientResponseAdapter;
import com.github.kristofa.brave.KeyValueAnnotation;
import zipkin.TraceKeys;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * @deprecated Replaced by {@code HttpClientParser} from brave-http
 */
@Deprecated
public class HttpClientResponseAdapter implements ClientResponseAdapter {

    private final HttpResponse response;

    public HttpClientResponseAdapter(HttpResponse response) {
        this.response = response;
    }

    @Override
    public Collection<KeyValueAnnotation> responseAnnotations() {
        int httpStatus = response.getHttpStatusCode();

        if ((httpStatus < 200) || (httpStatus > 299)) {
            return Collections.singleton(KeyValueAnnotation.create(
                    TraceKeys.HTTP_STATUS_CODE, String.valueOf(httpStatus)));
        }
        return Collections.emptyList();
    }

}
