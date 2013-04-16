package com.github.kristofa.brave;

/**
 * Annotation that can be assigned to a Span.
 * 
 * @author kristof
 * @see Span
 */
public interface Annotation {

    /**
     * Get time stamp for annotation.
     * 
     * @return Time stamp for annotation, when was annotation created. Milliseconds since Unix epoch.
     */
    long getTimeStamp();

    /**
     * Get annotation name.
     * 
     * @return Annotation name.
     */
    String getAnnotationName();

    /**
     * Optional duration for annotation.
     * 
     * @return Duration in milliseconds or <code>null</code> in case this annotation has no duration assigned.
     */
    Integer getDuration();

    /**
     * Get endpoint for annotation.
     * 
     * @return Endpoint for annotation.
     */
    EndPoint getEndPoint();

}
