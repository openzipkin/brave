package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Span;
import java.util.concurrent.Callable;

import com.github.kristofa.brave.internal.Nullable;
import com.google.auto.value.AutoValue;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Callable implementation that wraps another Callable and makes sure the wrapped Callable will be executed in the same
 * Span/Trace context as the thread from which the Callable was executed.
 * <p/>
 * Is used by {@link BraveExecutorService}.
 * 
 * @author kristof
 * @param <T> Return type.
 * @see BraveExecutorService
 * @deprecated Replaced by {@code brave.propagation.CurrentTraceContext}
 */
@Deprecated
@AutoValue
public abstract class BraveCallable<T> implements Callable<T> {

    /**
     * @since 3.17
     */
    public static <T> BraveCallable<T> wrap(Callable<T> callable, Brave brave) {
        checkNotNull(brave, "brave"); // auto-value will check the others.
        return new AutoValue_BraveCallable(
            callable,
            brave.localSpanThreadBinder(),
            brave.localSpanThreadBinder().getCurrentLocalSpan(),
            brave.serverSpanThreadBinder(),
            brave.serverSpanThreadBinder().getCurrentServerSpan()
        );
    }

    static <T> BraveCallable<T> wrap( // hidden for package-scoped use
        Callable<T> callable,
        LocalSpanThreadBinder localSpanThreadBinder,
        ServerSpanThreadBinder serverSpanThreadBinder
    ) {
        return new AutoValue_BraveCallable(
            callable,
            localSpanThreadBinder,
            localSpanThreadBinder.getCurrentLocalSpan(),
            serverSpanThreadBinder,
            serverSpanThreadBinder.getCurrentServerSpan()
        );
    }

    /**
     * @deprecated use {@link #wrap(Callable, Brave)} because this constructor loses thread
     * state for local span parents.
     */
    @Deprecated
    public static <T> BraveCallable<T> create(Callable<T> wrappedCallable, ServerSpanThreadBinder serverSpanThreadBinder) {
        checkNotNull(serverSpanThreadBinder, "serverSpanThreadBinder"); // auto-value will check the others.
        return new AutoValue_BraveCallable(
            wrappedCallable,
            null,
            null,
            serverSpanThreadBinder,
            serverSpanThreadBinder.getCurrentServerSpan()
        );
    }

    abstract Callable<T> wrappedCallable();
    @Nullable // while deprecated constructor is in use
    abstract LocalSpanThreadBinder localSpanThreadBinder();
    @Nullable
    abstract Span currentLocalSpan();
    abstract ServerSpanThreadBinder serverSpanThreadBinder();
    @Nullable
    abstract ServerSpan currentServerSpan();

    /**
     * {@inheritDoc}
     */
    @Override
    public T call() throws Exception {
        if (localSpanThreadBinder() == null) { // old behavior
            serverSpanThreadBinder().setCurrentSpan(currentServerSpan());
            return wrappedCallable().call();
        }
        ServerSpan previousServerSpan = serverSpanThreadBinder().getCurrentServerSpan();
        Span previousLocalSpan = localSpanThreadBinder().getCurrentLocalSpan();
        try {
            serverSpanThreadBinder().setCurrentSpan(currentServerSpan());
            localSpanThreadBinder().setCurrentSpan(currentLocalSpan());
            return wrappedCallable().call();
        } finally {
            serverSpanThreadBinder().setCurrentSpan(previousServerSpan);
            localSpanThreadBinder().setCurrentSpan(previousLocalSpan);
        }
    }

    BraveCallable() {
    }
}
