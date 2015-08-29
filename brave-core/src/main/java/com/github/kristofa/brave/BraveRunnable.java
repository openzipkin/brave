package com.github.kristofa.brave;

import com.github.kristofa.brave.internal.Nullable;
import com.google.auto.value.AutoValue;

/**
 * {@link Runnable} implementation that wraps another Runnable and makes sure the wrapped Runnable will be executed in the
 * same Span/Trace context as the thread from which the Runnable was executed.
 * <p/>
 * Is used by {@link BraveExecutorService}.
 * 
 * @author kristof
 * @see BraveExecutorService
 */
@AutoValue
public abstract class BraveRunnable implements Runnable {

    /**
     * Creates a new instance.
     *
     * @param runnable The wrapped Callable.
     * @param serverSpanThreadBinder ServerSpan thread binder.
     */
    public static BraveRunnable create(Runnable runnable, ServerSpanThreadBinder serverSpanThreadBinder) {
        return new AutoValue_BraveRunnable(runnable, serverSpanThreadBinder, serverSpanThreadBinder.getCurrentServerSpan());
    }

    abstract Runnable wrappedRunnable();
    abstract ServerSpanThreadBinder serverSpanThreadBinder();
    @Nullable
    abstract ServerSpan currentServerSpan();

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        serverSpanThreadBinder().setCurrentSpan(currentServerSpan());
        wrappedRunnable().run();
    }
}
