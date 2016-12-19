package com.github.kristofa.brave.cxf3;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerSpan;
import com.github.kristofa.brave.ServerSpanThreadBinder;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseInterceptorChain;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

/**
 * Wraps around {@link ExecutorService} and passes {@link ServerSpan}
 * from cxf {@link org.apache.cxf.message.Exchange} context (if exists)
 * to current {@link ServerSpanThreadBinder} during execution.
 */
public class BraveCxfExecutorService implements ExecutorService, Closeable {

  private final ExecutorService delegate;
  private final ServerSpanThreadBinder serverSpanThreadBinder;

  public static BraveCxfExecutorService wrap(ExecutorService delegate, Brave brave) {
    return new BraveCxfExecutorService(delegate, brave);
  }

  BraveCxfExecutorService(ExecutorService delegate, Brave brave) {
    this.delegate = delegate;
    this.serverSpanThreadBinder = brave.serverSpanThreadBinder();
  }

  @Override
  public void close() throws IOException {
    shutdown();
  }

  @Override
  public void execute(Runnable command) {
    delegate.execute(wrap(command));
  }

  @Override
  public void shutdown() {
    delegate.shutdown();
  }

  @Override
  public List<Runnable> shutdownNow() {
    return delegate.shutdownNow();
  }

  @Override
  public boolean isShutdown() {
    return delegate.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return delegate.isTerminated();
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return delegate.awaitTermination(timeout, unit);
  }

  @Override
  public <T> Future<T> submit(Callable<T> task) {
    return delegate.submit(wrap(task));
  }

  @Override
  public <T> Future<T> submit(Runnable task, T result) {
    return delegate.submit(wrap(task), result);
  }

  @Override
  public Future<?> submit(Runnable task) {
    return delegate.submit(wrap(task));
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
    return delegate.invokeAll(wrap(tasks));
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
    return delegate.invokeAll(wrap(tasks), timeout, unit);
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
    return delegate.invokeAny(wrap(tasks));
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    return delegate.invokeAny(wrap(tasks), timeout, unit);
  }

  private <T> T getFromExchange(String key) {
    Message message = PhaseInterceptorChain.getCurrentMessage();
    if (message != null && message.getExchange() != null) {
      return (T) message.getExchange().get(key);
    } else {
      return null;
    }
  }

  private Runnable wrap(Runnable command) {
    final ServerSpan serverSpan = getFromExchange(BraveCxfConstants.BRAVE_SERVER_SPAN);
    final ServerSpan previous = serverSpanThreadBinder.getCurrentServerSpan();

    return () -> {
      try {
        if (serverSpan != null) {
          serverSpanThreadBinder.setCurrentSpan(serverSpan);
        }
        command.run();
      } finally {
        if (serverSpan != null) {
          serverSpanThreadBinder.setCurrentSpan(previous);
        }
      }
    };
  }

  private <V> Callable<V> wrap(Callable<V> command) {
    final ServerSpan serverSpan = getFromExchange(BraveCxfConstants.BRAVE_SERVER_SPAN);
    final ServerSpan previous = serverSpanThreadBinder.getCurrentServerSpan();

    return () -> {
      try {
        if (serverSpan != null) {
          serverSpanThreadBinder.setCurrentSpan(serverSpan);
        }
        return command.call();
      } finally {
        if (serverSpan != null) {
          serverSpanThreadBinder.setCurrentSpan(previous);
        }
      }
    };
  }

  private <T> Collection<? extends Callable<T>> wrap(final Collection<? extends Callable<T>> originalCollection) {
    final Collection<Callable<T>> collection = new ArrayList<>();
    for (final Callable<T> t : originalCollection) {
      collection.add(wrap(t));
    }
    return collection;
  }
}