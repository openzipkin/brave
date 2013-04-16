package com.github.kristofa.brave;

/**
 * Used to submit application specific annotations.
 * 
 * @author kristof
 */
public interface AnnotationSubmitter {

    /**
     * Submits custom annotation for current span. Use this method if your annotation has a duration assigned to it.
     * 
     * @param annotationName Custom annotation for current span.
     * @param duration Duration in milliseconds.
     */
    void submitAnnotation(final String annotationName, final int duration);

    /**
     * Submits custom annotation for current span. Use this method if your annotation has no duration assigned to it.
     * 
     * @param annotationName Custom annotation for current span.
     */
    void submitAnnotation(final String annotationName);

}
