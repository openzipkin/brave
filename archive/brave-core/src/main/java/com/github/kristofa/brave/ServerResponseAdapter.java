package com.github.kristofa.brave;


import java.util.Collection;

/**
 * @deprecated Replaced by {@code HttpServerParser} from brave-http or {@code brave.SpanCustomizer}
 * if not http.
 */
@Deprecated
public interface ServerResponseAdapter {

    /**
     * Returns a collection of annotations that should be added to span
     * before returning response.
     *
     * Can be used to indicate more details about response.
     * For example information on error or unsuccessful reponse.
     *
     * @return Collection of annotations.
     */
    Collection<KeyValueAnnotation> responseAnnotations();
}
