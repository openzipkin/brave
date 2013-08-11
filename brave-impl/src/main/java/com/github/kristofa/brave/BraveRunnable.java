package com.github.kristofa.brave;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * {@link Runnable} implementation that wraps another Runnable and makes sure the wrapped Runnable will be executed in the
 * same Span/Trace context as the thread from which the Runnable was executed.
 * <p/>
 * Is used by {@link BraveExecutorService}.
 * 
 * @author kristof
 * @see BraveExecutorService
 */
public class BraveRunnable implements Runnable {

    private final Runnable wrappedRunnable;
    private final ServerSpan currentServerSpan;
    private final ServerSpanThreadBinder serverSpanThreadBinder;

    /**
     * Creates a new instance.
     * 
     * @param runnable The wrapped Runnable.
     * @param serverTracer ServerTracer.
     * @param currentServerSpan Current ServerSpan. This ServerSpan will also get binded to the wrapped thread.
     */
    public BraveRunnable(final Runnable runnable, final ServerSpanThreadBinder serverTracer) {
        wrappedRunnable = runnable;
        serverSpanThreadBinder = serverTracer;
        currentServerSpan = serverTracer.getCurrentServerSpan();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        serverSpanThreadBinder.setCurrentSpan(currentServerSpan);
        final long start = System.currentTimeMillis();
        try {
            wrappedRunnable.run();
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
