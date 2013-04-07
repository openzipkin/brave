package com.github.kristofa.brave;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.Validate;

/**
 * Span implementation.
 * 
 * @see Span
 * @author kristof
 */
class SpanImpl implements Span {

    private final SpanId id;
    private final String name;
    private final Set<Annotation> annotations = new HashSet<Annotation>();

    /**
     * Create a new instance.
     * 
     * @param id Span id. Should not be <code>null</code>.
     * @param name Span name. Should not be <code>null</code> or empty.
     */
    SpanImpl(final SpanId id, final String name) {
        Validate.notNull(id);
        Validate.notEmpty(name);
        this.id = id;
        this.name = name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SpanId getSpanId() {
        return id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Annotation> getAnnotations() {
        return Collections.unmodifiableSet(annotations);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAnnotation(final Annotation annotation) {
        annotations.add(annotation);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + annotations.hashCode();
        result = prime * result + id.hashCode();
        result = prime * result + name.hashCode();
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final SpanImpl other = (SpanImpl)obj;
        if (!annotations.equals(other.annotations)) {
            return false;
        }
        if (!id.equals(other.id)) {
            return false;
        }
        if (!name.equals(other.name)) {
            return false;
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "[id: " + id + ", name: " + name + ", annotations: " + annotations + "]";
    }

}
