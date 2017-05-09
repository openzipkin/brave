package com.github.kristofa.brave;

import com.github.kristofa.brave.internal.Nullable;
import com.google.auto.value.AutoValue;
import com.twitter.zipkin.gen.Span;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

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
     * @since 3.17
     */
    public static BraveRunnable wrap(Runnable runnable, Brave brave) {
        checkNotNull(brave, "brave"); // auto-value will check the others.
        return new AutoValue_BraveRunnable(
            runnable,
            brave.localSpanThreadBinder(),
            brave.localSpanThreadBinder().getCurrentLocalSpan(),
            brave.serverSpanThreadBinder(),
            brave.serverSpanThreadBinder().getCurrentServerSpan()
        );
    }

    static BraveRunnable wrap( // hidden for package-scoped use
        Runnable runnable,
        LocalSpanThreadBinder localSpanThreadBinder,
        ServerSpanThreadBinder serverSpanThreadBinder
    ) {
        return new AutoValue_BraveRunnable(
            runnable,
            localSpanThreadBinder,
            localSpanThreadBinder.getCurrentLocalSpan(),
            serverSpanThreadBinder,
            serverSpanThreadBinder.getCurrentServerSpan()
        );
    }

    /**
     * @deprecated use {@link #wrap(Runnable, Brave)} because this constructor loses thread
     * state for local span parents.
     */
    @Deprecated
    public static BraveRunnable create(Runnable runnable, ServerSpanThreadBinder serverSpanThreadBinder) {
        checkNotNull(serverSpanThreadBinder, "serverSpanThreadBinder"); // auto-value will check the others.
        return new AutoValue_BraveRunnable(
            runnable,
            null,
            null,
            serverSpanThreadBinder,
            serverSpanThreadBinder.getCurrentServerSpan()
        );
    }

    abstract Runnable wrappedRunnable();
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
    public void run() {
      if (localSpanThreadBinder() == null) { // old behavior
        serverSpanThreadBinder().setCurrentSpan(currentServerSpan());
        wrappedRunnable().run();
        return;
      }

      ServerSpan previousServerSpan = serverSpanThreadBinder().getCurrentServerSpan();
      Span previousLocalSpan = localSpanThreadBinder().getCurrentLocalSpan();
      try {
        serverSpanThreadBinder().setCurrentSpan(currentServerSpan());
        localSpanThreadBinder().setCurrentSpan(currentLocalSpan());
        wrappedRunnable().run();
      } finally {
        serverSpanThreadBinder().setCurrentSpan(previousServerSpan);
        localSpanThreadBinder().setCurrentSpan(previousLocalSpan);
      }
    }
}
