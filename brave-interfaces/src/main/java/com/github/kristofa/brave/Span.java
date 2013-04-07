package com.github.kristofa.brave;

import java.util.Set;

/**
 * A full span contains a client and server part.
 * <p/>
 * The client part should define at least following annotations: CLIENT_SENT, CLIENT_RECEIVED.<br/>
 * The server part should define at least following annotations: SERVER_RECEIVED, SERVER_SENT.<br/>
 * <p/>
 * The actual order will off course be: CLIENT_SENT, SERVER_RECEIVED, SERVER_SENT, CLIENT_RECEIVED.
 * 
 * @author kristof
 */
public interface Span {

    /**
     * Gets span identifier.
     * 
     * @return Gets span identifier.
     */
    SpanId getSpanId();

    /**
     * Name of span. Should identify request.
     * 
     * @return Name for span.
     */
    String getName();

    /**
     * Get annotations for span.
     * 
     * @return annotations for span. Returns empty set in case there are no annotations defined.
     */
    Set<Annotation> getAnnotations();

    /**
     * Adds an annotation to span.
     * 
     * @param annotation Annotation to add to span.
     */
    void addAnnotation(final Annotation annotation);

}
