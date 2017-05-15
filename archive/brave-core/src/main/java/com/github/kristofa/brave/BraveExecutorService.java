package com.github.kristofa.brave;

import com.github.kristofa.brave.internal.Nullable;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * {@link ExecutorService} that wraps around an existing {@link ExecutorService} and that makes sure the threads are executed
 * in the same Span/Trace context as the the thread that invoked execution of the threads.
 * <p/>
 * It uses {@link ServerTracer} and {@link ServerSpanThreadBinder} to accomplish this in a transparent way for the user.
 * <p/>
 * It also implements {@link Closeable}, calling {@link BraveExecutorService#shutdown()}, so the executor service is
 * shut down properly when for example using Spring.
 * 
 * @author kristof
 * @see BraveCallable
 * @see BraveRunnable
 * @deprecated Replaced by {@code brave.propagation.CurrentTraceContext}
 */
@Deprecated
public class BraveExecutorService implements ExecutorService, Closeable {

    /**
     * @since 3.17
     */
    public static BraveExecutorService wrap(ExecutorService wrappedExecutor, Brave brave) {
        return new BraveExecutorService(wrappedExecutor, brave);
    }

    private final ExecutorService wrappedExecutor;
    private final ServerSpanThreadBinder serverSpanThreadBinder;

    @Nullable // when using deprecated constructor
    private final LocalSpanThreadBinder localSpanThreadBinder;

    BraveExecutorService(ExecutorService wrappedExecutor, Brave brave) { // intentionally hidden
        this.wrappedExecutor = checkNotNull(wrappedExecutor, "wrappedExecutor");
        checkNotNull(brave, "brave");
        this.localSpanThreadBinder = brave.localSpanThreadBinder();
        this.serverSpanThreadBinder = brave.serverSpanThreadBinder();
    }

    /**
     * @deprecated use {@link #wrap(ExecutorService, Brave)} because this constructor loses thread
     * state for local span parents.
     */
    @Deprecated
    public BraveExecutorService(final ExecutorService wrappedExecutor, final ServerSpanThreadBinder serverSpanThreadBinder) {
        this.wrappedExecutor = checkNotNull(wrappedExecutor, "Null wrappedExecutor");
        this.localSpanThreadBinder = null;
        this.serverSpanThreadBinder = checkNotNull(serverSpanThreadBinder, "Null serverSpanThreadBinder");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(final Runnable arg0) {
        wrappedExecutor.execute(wrap(arg0));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
        return wrappedExecutor.awaitTermination(timeout, unit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> arg0) throws InterruptedException {
        return wrappedExecutor.invokeAll(wrap(arg0));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> arg0, final long arg1, final TimeUnit arg2)
        throws InterruptedException {
        return wrappedExecutor.invokeAll(wrap(arg0), arg1, arg2);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T invokeAny(final Collection<? extends Callable<T>> arg0) throws InterruptedException, ExecutionException {
        return wrappedExecutor.invokeAny(wrap(arg0));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T invokeAny(final Collection<? extends Callable<T>> arg0, final long arg1, final TimeUnit arg2)
        throws InterruptedException, ExecutionException, TimeoutException {
        return wrappedExecutor.invokeAny(wrap(arg0), arg1, arg2);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isShutdown() {
        return wrappedExecutor.isShutdown();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isTerminated() {
        return wrappedExecutor.isTerminated();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdown() {
        wrappedExecutor.shutdown();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Runnable> shutdownNow() {
        return wrappedExecutor.shutdownNow();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> Future<T> submit(final Callable<T> arg0) {
        return wrappedExecutor.submit(wrap(arg0));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<?> submit(Runnable arg0) {
        return wrappedExecutor.submit(wrap(arg0));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> Future<T> submit(final Runnable arg0, final T arg1) {
        return wrappedExecutor.submit(wrap(arg0), arg1);
    }

    private <T> Collection<? extends Callable<T>> wrap(
        final Collection<? extends Callable<T>> originalCollection) {
        final Collection<Callable<T>> collection = new ArrayList<Callable<T>>();
        for (final Callable<T> t : originalCollection) {
            collection.add(wrap(t));
        }
        return collection;
    }

    /**
     * Convenience for try-with-resources, or frameworks such as Spring that automatically process this.
     **/
    @Override
    public void close() {
        shutdown();
    }

    // avoids deprecated BraveRunnable factory when we weren't called with a deprecated constructor
    BraveRunnable wrap(Runnable arg0) {
        return localSpanThreadBinder == null
            ? BraveRunnable.create(arg0, serverSpanThreadBinder)
            : BraveRunnable.wrap(arg0, localSpanThreadBinder, serverSpanThreadBinder);
    }

    // avoids deprecated BraveCallable factory when we weren't called with a deprecated constructor
    <T> BraveCallable<T> wrap(Callable<T> arg0) {
        return localSpanThreadBinder == null
            ? BraveCallable.create(arg0, serverSpanThreadBinder)
            : BraveCallable.wrap(arg0, localSpanThreadBinder, serverSpanThreadBinder);
    }
}
