package com.github.kristofa.brave;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.PreDestroy;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * {@link ExecutorService} that wraps around an existing {@link ExecutorService} and that makes sure the threads are executed
 * in the same Span/Trace context as the the thread that invoked execution of the threads.
 * <p/>
 * It uses {@link ServerTracer} and {@link ServerSpanThreadBinder} to accomplish this in a transparent way for the user.
 * <p/>
 * It also has {@link PreDestroy} annotation on {@link BraveExecutorService#shutdown()} method so the executor service is
 * shut down properly when for example using Spring.
 * 
 * @author kristof
 * @see BraveCallable
 * @see BraveRunnable
 */
public class BraveExecutorService implements ExecutorService {

    private final ExecutorService wrappedExecutor;
    private final ServerSpanThreadBinder threadBinder;

    /**
     * Creates a new instance.
     * 
     * @param wrappedExecutor Wrapped ExecutorService to which execution will be delegated.
     * @param threadBinder Thread binder.
     */
    public BraveExecutorService(final ExecutorService wrappedExecutor, final ServerSpanThreadBinder threadBinder) {
        this.wrappedExecutor = checkNotNull(wrappedExecutor, "Null wrappedExecutor");
        this.threadBinder = checkNotNull(threadBinder, "Null threadBinder");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(final Runnable arg0) {
        final BraveRunnable braveRunnable = BraveRunnable.create(arg0, threadBinder);
        wrappedExecutor.execute(braveRunnable);
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

        return wrappedExecutor.invokeAll(buildBraveCollection(arg0));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> arg0, final long arg1, final TimeUnit arg2)
        throws InterruptedException {
        return wrappedExecutor.invokeAll(buildBraveCollection(arg0), arg1, arg2);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T invokeAny(final Collection<? extends Callable<T>> arg0) throws InterruptedException, ExecutionException {
        return wrappedExecutor.invokeAny(buildBraveCollection(arg0));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T invokeAny(final Collection<? extends Callable<T>> arg0, final long arg1, final TimeUnit arg2)
        throws InterruptedException, ExecutionException, TimeoutException {
        return wrappedExecutor.invokeAny(buildBraveCollection(arg0), arg1, arg2);
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
    @PreDestroy
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
        final BraveCallable<T> braveCallable = BraveCallable.create(arg0, threadBinder);
        return wrappedExecutor.submit(braveCallable);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<?> submit(final Runnable arg0) {
        final BraveRunnable braveRunnable = BraveRunnable.create(arg0, threadBinder);
        return wrappedExecutor.submit(braveRunnable);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> Future<T> submit(final Runnable arg0, final T arg1) {
        final BraveRunnable braveRunnable = BraveRunnable.create(arg0, threadBinder);
        return wrappedExecutor.submit(braveRunnable, arg1);
    }

    private <T> Collection<? extends Callable<T>> buildBraveCollection(
        final Collection<? extends Callable<T>> originalCollection) {
        final Collection<Callable<T>> collection = new ArrayList<Callable<T>>();
        for (final Callable<T> t : originalCollection) {
            collection.add(BraveCallable.create(t, threadBinder));
        }
        return collection;
    }

}
