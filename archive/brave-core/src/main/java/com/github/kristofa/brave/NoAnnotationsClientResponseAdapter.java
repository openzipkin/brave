package com.github.kristofa.brave;

import java.util.Collection;
import java.util.Collections;

/**
 * @deprecated Replaced by {@code HttpClientParser} from brave-http or {@code brave.SpanCustomizer}
 * if not http.
 */
@Deprecated
public class NoAnnotationsClientResponseAdapter implements ClientResponseAdapter {

    private final static ClientResponseAdapter INSTANCE = new NoAnnotationsClientResponseAdapter();

    private final static Collection<KeyValueAnnotation> EMPTY = Collections.emptyList();

    private NoAnnotationsClientResponseAdapter() { }


    public static ClientResponseAdapter getInstance() {
        return INSTANCE;
    }

    @Override
    public Collection<KeyValueAnnotation> responseAnnotations() {
        return EMPTY;
    }
}
