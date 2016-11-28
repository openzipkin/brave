package com.github.kristofa.brave;

import java.util.Collection;

public interface ClientResponseAdapter {

    interface Factory<R> {
        ClientResponseAdapter create(R response);
    }

    /**
     * Returns a collection of annotations that should be added to span
     * based on response.
     *
     * Can be used to indicate errors when response was not successful.
     *
     * @return Collection of annotations.
     */
    Collection<KeyValueAnnotation> responseAnnotations();
}
