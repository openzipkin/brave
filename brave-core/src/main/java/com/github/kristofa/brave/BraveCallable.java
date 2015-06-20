package com.github.kristofa.brave;

import com.google.auto.value.AutoValue;

import java.util.concurrent.Callable;

import javax.annotation.Nullable;

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
@AutoValue
public abstract class BraveCallable<T> implements Callable<T> {

    /**
     * Creates a new instance.
     *
     * @param wrappedCallable The wrapped Callable.
     * @param serverSpanThreadBinder ServerSpan thread binder.
     */
    public static <T> BraveCallable<T> create(Callable<T> wrappedCallable, ServerSpanThreadBinder serverSpanThreadBinder) {
        return new AutoValue_BraveCallable<T>(wrappedCallable, serverSpanThreadBinder, serverSpanThreadBinder.getCurrentServerSpan());
    }

    abstract Callable<T> wrappedCallable();
    abstract ServerSpanThreadBinder serverSpanThreadBinder();
    @Nullable
    abstract ServerSpan currentServerSpan();

    /**
     * {@inheritDoc}
     */
    @Override
    public T call() throws Exception {
        serverSpanThreadBinder().setCurrentSpan(currentServerSpan());
        final long start = System.currentTimeMillis();
        try {
            return wrappedCallable().call();
        } finally {
            final long duration = System.currentTimeMillis() - start;
            currentServerSpan().incThreadDuration(duration);
        }
    }

    BraveCallable() {
    }
}
