package com.github.kristofa.brave;

import java.util.concurrent.Callable;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Callable implementation that wraps another Callable and makes sure the wrapped Callable will be executed in the same
 * Span/Trace context as the thread from which the Callable was executed.
 * <p/>
 * Is used by {@link BraveExecutorService}.
 * 
 * @author kristof
 * @param <T> Return type.
 * @see BraveExecutorService
 */
public class BraveCallable<T> implements Callable<T> {

    private final Callable<T> wrappedCallable;
    private final ServerSpanThreadBinder serverTracer;
    private final ServerSpan currentServerSpan;

    /**
     * Creates a new instance.
     * 
     * @param wrappedCallable The wrapped Callable.
     * @param serverSpanThreadBinder ServerSpan thread binder.
     */
    BraveCallable(final Callable<T> wrappedCallable, final ServerSpanThreadBinder serverSpanThreadBinder) {
        this.wrappedCallable = wrappedCallable;
        this.serverTracer = serverSpanThreadBinder;
        this.currentServerSpan = serverSpanThreadBinder.getCurrentServerSpan();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T call() throws Exception {
        serverTracer.setCurrentSpan(currentServerSpan);
        final long start = System.currentTimeMillis();
        try {
            return wrappedCallable.call();
        } finally {
            final long duration = System.currentTimeMillis() - start;
            currentServerSpan.incThreadDuration(duration);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object arg0) {
        return EqualsBuilder.reflectionEquals(this, arg0, false);
    }

}
