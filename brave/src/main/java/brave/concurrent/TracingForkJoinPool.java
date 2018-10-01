package brave.concurrent;

import brave.Tracing;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TracingForkJoinPool extends ForkJoinPool {

  /**
   *
   * @param tracing
   * @param parallelism
   * @param factory
   * @param uncaughtExceptionHandler
   * @param asyncmode
   * @return
   */
  public static final ForkJoinPool wrap(Tracing tracing, Integer parallelism,
      ForkJoinWorkerThreadFactory factory, Thread.UncaughtExceptionHandler uncaughtExceptionHandler,
      boolean asyncmode) {
    if (parallelism == null) {
      return new TracingForkJoinPool(tracing);
    }
    return new TracingForkJoinPool(tracing, parallelism, factory, uncaughtExceptionHandler, asyncmode);
  }

  private Tracing tracing;

  private TracingForkJoinPool(Tracing tracing) {
    super();
    this.tracing = tracing;
  }

  private TracingForkJoinPool(Tracing tracing, int parallelism,
      ForkJoinWorkerThreadFactory factory, Thread.UncaughtExceptionHandler uncaughtExceptionHandler,
      boolean asyncmode) {
    super(parallelism, factory, uncaughtExceptionHandler, asyncmode);
    this.tracing = tracing;
  }

  @Override public <T> T invoke(ForkJoinTask<T> task) {
    return super.invoke(task);
  }

  @Override public void execute(ForkJoinTask<?> task) {
    super.execute(task);
  }

  @Override public void execute(Runnable task) {
    super.execute(tracing.currentTraceContext().wrap(task));
  }

  @Override public <T> ForkJoinTask<T> submit(ForkJoinTask<T> task) {
    return super.submit(task);
  }

  @Override public <T> ForkJoinTask<T> submit(Callable<T> task) {
    return super.submit(tracing.currentTraceContext().wrap(task));
  }

  @Override public <T> ForkJoinTask<T> submit(Runnable task, T result) {
    return super.submit(tracing.currentTraceContext().wrap(task), result);
  }

  @Override public ForkJoinTask<?> submit(Runnable task) {
    return super.submit(tracing.currentTraceContext().wrap(task));
  }

  @Override public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
    return super.invokeAll(wrapCallables(tasks));
  }

  @Override protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
    return super.newTaskFor(tracing.currentTraceContext().wrap(runnable), value);
  }

  @Override protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
    return super.newTaskFor(tracing.currentTraceContext().wrap(callable));
  }

  @Override public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
      throws InterruptedException, ExecutionException {
    return super.invokeAny(wrapCallables(tasks));
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    return super.invokeAny(wrapCallables(tasks), timeout, unit);
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout,
      TimeUnit unit) throws InterruptedException {
    return super.invokeAll(wrapCallables(tasks), timeout, unit);
  }

  private <T> Collection<? extends Callable<T>> wrapCallables(Collection<? extends Callable<T>> tasks) {
    Collection<Callable<T>> wrappedTasks = new ArrayList<>();
    for (Callable<T> callable : tasks) {
      wrappedTasks.add(tracing.currentTraceContext().wrap(callable));
    }
    return wrappedTasks;
  }
}
