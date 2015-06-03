package com.github.kristofa.brave;


import java.util.Collection;

public interface ClientResponseAdapter {

    /**
     * Returns a collection of annotations that should be added to span
     * based on response.
     *
     * Can be used to indicate errors.
     *
     * @return Collection of annotations.
     */
    Collection<KeyValueAnnotation> responseAnnotations();
}
