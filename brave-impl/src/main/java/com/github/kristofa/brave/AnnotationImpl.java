package com.github.kristofa.brave;

import org.apache.commons.lang3.Validate;

/**
 * Annotation implementation.
 * 
 * @see Annotation
 * @author kristof
 */
class AnnotationImpl implements Annotation {

    private final long timeStamp;
    private final Long duration;
    private final String annotationName;
    private final EndPoint endpoint;

    /**
     * Creates a new instance.
     * 
     * @param annotationName Annotation name. Should not be <code>null</code> or empty.
     * @param endpoint Endpoint. Should not be <code>null</code>.
     */
    AnnotationImpl(final String annotationName, final EndPoint endpoint) {
        this(annotationName, endpoint, null);
    }

    /**
     * Creates a new instance.
     * 
     * @param annotationName Annotation name. Should not be <code>null</code> or empty.
     * @param endpoint Endpoint. Should not be <code>null</code>.
     * @param duration Optional duration in microseconds. Can be <code>null</code> but if specified it should be >= 0.
     */
    AnnotationImpl(final String annotationName, final EndPoint endpoint, final Long duration) {
        Validate.notEmpty(annotationName);
        Validate.notNull(endpoint);
        Validate.isTrue(duration == null || duration >= 0);
        this.annotationName = annotationName;
        this.endpoint = endpoint;
        this.duration = duration;
        timeStamp = System.nanoTime();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getTimeStamp() {
        return timeStamp;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAnnotationName() {
        return annotationName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long getDuration() {
        return duration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EndPoint getEndPoint() {
        return endpoint;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + annotationName.hashCode();
        result = prime * result + (duration == null ? 0 : duration.hashCode());
        result = prime * result + endpoint.hashCode();
        return result;
    }

    /**
     * {@inheritDoc}
     */
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
        final AnnotationImpl other = (AnnotationImpl)obj;
        if (!annotationName.equals(other.annotationName)) {
            return false;
        }
        if (duration == null) {
            if (other.duration != null) {
                return false;
            }
        } else if (!duration.equals(other.duration)) {
            return false;
        }
        if (!endpoint.equals(other.endpoint)) {
            return false;
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "[annotation name: " + annotationName + ", end point: " + endpoint.toString() + ", timestamp: "
            + getTimeStamp() + ", duration: " + (duration == null ? "null" : duration) + "]";
    }

}
