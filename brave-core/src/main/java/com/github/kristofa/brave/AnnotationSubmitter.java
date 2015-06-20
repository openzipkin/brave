package com.github.kristofa.brave;

/**
 * Used to submit application specific annotations.
 * 
 * @author kristof
 */
public interface AnnotationSubmitter {

    /**
     * Submits custom annotation that represents an event with duration.
     * 
     * @param annotationName Custom annotation.
     * @param startTime Start time, <a href="http://en.wikipedia.org/wiki/Unix_time">Unix time</a> in milliseconds. eg
     *            System.currentTimeMillis().
     * @param endTime End time, Unix time in milliseconds. eg System.currentTimeMillis().
     */
    void submitAnnotation(final String annotationName, final long startTime, final long endTime);

    /**
     * Submits custom annotation for current span. Use this method if your annotation has no duration assigned to it.
     * 
     * @param annotationName Custom annotation for current span.
     */
    void submitAnnotation(final String annotationName);

    /**
     * Submits a binary (key/value) annotation with String value.
     * 
     * @param key Key, should not be blank.
     * @param value String value, should not be <code>null</code>.
     */
    void submitBinaryAnnotation(final String key, final String value);

    /**
     * Submits a binary (key/value) annotation with int value.
     * 
     * @param key Key, should not be blank.
     * @param value Integer value.
     */
    void submitBinaryAnnotation(final String key, final int value);

}
