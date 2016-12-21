package com.github.kristofa.brave;

import com.github.kristofa.brave.example.TestServerClientAndLocalSpanStateCompilation;
import com.twitter.zipkin.gen.Span;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.Test;
import zipkin.reporter.Reporter;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class BraveExecutorServiceTest {
  // Ensures one at-a-time, but also on a different thread
  ExecutorService wrappedExecutor = Executors.newSingleThreadExecutor();
  // Ensures things don't accidentally work due to inheritable thread locals!
  Brave brave = new Brave.Builder(new TestServerClientAndLocalSpanStateCompilation())
      .reporter(Reporter.NOOP)
      .traceSampler(Sampler.ALWAYS_SAMPLE).build();
  BlockingQueue<Span> spanQueue = new LinkedBlockingQueue();
  Supplier<Span> currentServerSpan =
      () -> brave.serverSpanThreadBinder().getCurrentServerSpan().getSpan();
  Supplier<Span> createServerSpan = () -> {
    brave.serverTracer().setStateUnknown("test");
    return currentServerSpan.get();
  };
  Supplier<Span> currentLocalSpan =
      () -> brave.localSpanThreadBinder().getCurrentLocalSpan();
  Supplier<Span> createLocalSpan = () -> {
    brave.localTracer().startNewSpan(getClass().getSimpleName(), "test");
    return currentLocalSpan.get();
  };

  public void close() throws InterruptedException {
    wrappedExecutor.shutdownNow();
    wrappedExecutor.awaitTermination(1, TimeUnit.SECONDS);
  }

  @Test
  public void execute_serverParent_deprecatedConstructor() throws Exception {
    ExecutorService executor =
        new BraveExecutorService(wrappedExecutor, brave.serverSpanThreadBinder());
    eachExecuteHasCorrectSpanAttached(executor, createServerSpan, currentServerSpan);
  }

  @Test
  public void execute_serverParent() throws Exception {
    ExecutorService executor = BraveExecutorService.wrap(wrappedExecutor, brave);
    eachExecuteHasCorrectSpanAttached(executor, createServerSpan, currentServerSpan);
  }

  @Test(expected = AssertionError.class)
  public void execute_localParent_deprecatedConstructor() throws Exception {
    ExecutorService executor =
        new BraveExecutorService(wrappedExecutor, brave.serverSpanThreadBinder());
    eachExecuteHasCorrectSpanAttached(executor, createLocalSpan, currentLocalSpan);
  }

  @Test
  public void execute_localParent() throws Exception {
    ExecutorService executor = BraveExecutorService.wrap(wrappedExecutor, brave);
    eachExecuteHasCorrectSpanAttached(executor, createLocalSpan, currentLocalSpan);
  }

  void eachExecuteHasCorrectSpanAttached(ExecutorService executor, Supplier<Span> createSpan,
      Supplier<Span> currentSpan) throws Exception {
    eachTaskHasCorrectSpanAttached(createSpan, () -> {
      executor.execute(() -> {
        spanQueue.add(currentSpan.get());
        sleepABit(); // delay here will block the queue
      });
      // this won't run immediately because the other is blocked
      executor.execute(() -> spanQueue.add(currentSpan.get()));
      return null;
    });
  }

  @Test
  public void submit_serverParent_deprecatedConstructor() throws Exception {
    ExecutorService executor =
        new BraveExecutorService(wrappedExecutor, brave.serverSpanThreadBinder());
    eachSubmitHasCorrectSpanAttached(executor, createServerSpan, currentServerSpan);
  }

  @Test
  public void submit_serverParent() throws Exception {
    ExecutorService executor = BraveExecutorService.wrap(wrappedExecutor, brave);
    eachSubmitHasCorrectSpanAttached(executor, createServerSpan, currentServerSpan);
  }

  // This is expected to fail because the old constructor does not have a means to set the current
  // local span.
  @Test(expected = AssertionError.class)
  public void submit_localParent_deprecatedConstructor() throws Exception {
    ExecutorService executor =
        new BraveExecutorService(wrappedExecutor, brave.serverSpanThreadBinder());
    eachSubmitHasCorrectSpanAttached(executor, createLocalSpan, currentLocalSpan);
  }

  @Test
  public void submit_localParent() throws Exception {
    ExecutorService executor = BraveExecutorService.wrap(wrappedExecutor, brave);
    eachSubmitHasCorrectSpanAttached(executor, createLocalSpan, currentLocalSpan);
  }

  void eachSubmitHasCorrectSpanAttached(ExecutorService executor, Supplier<Span> createSpan,
      Supplier<Span> currentSpan) throws Exception {
    eachTaskHasCorrectSpanAttached(createSpan, () -> {
      executor.submit(() -> {
        spanQueue.add(currentSpan.get());
        sleepABit(); // delay here will block the queue
      });
      // this won't run immediately because the other is blocked
      return executor.submit(() -> spanQueue.add(currentSpan.get()));
    });
  }

  @Test
  public void invokeAll_serverParent_deprecatedConstructor() throws Exception {
    ExecutorService executor =
        new BraveExecutorService(wrappedExecutor, brave.serverSpanThreadBinder());
    eachInvokeAllHasCorrectSpanAttached(executor, createServerSpan, currentServerSpan);
  }

  @Test
  public void invokeAll_serverParent() throws Exception {
    ExecutorService executor = BraveExecutorService.wrap(wrappedExecutor, brave);
    eachInvokeAllHasCorrectSpanAttached(executor, createServerSpan, currentServerSpan);
  }

  // TODO: not sure why this works while submit doesn't!
  @Test
  public void invokeAll_localParent_deprecatedConstructor() throws Exception {
    ExecutorService executor =
        new BraveExecutorService(wrappedExecutor, brave.serverSpanThreadBinder());
    eachInvokeAllHasCorrectSpanAttached(executor, createLocalSpan, currentLocalSpan);
  }

  @Test
  public void invokeAll_localParent() throws Exception {
    ExecutorService executor = BraveExecutorService.wrap(wrappedExecutor, brave);
    eachInvokeAllHasCorrectSpanAttached(executor, createLocalSpan, currentLocalSpan);
  }

  void eachInvokeAllHasCorrectSpanAttached(ExecutorService executor, Supplier<Span> createSpan,
      Supplier<Span> currentSpan) throws Exception {
    eachTaskHasCorrectSpanAttached(createSpan, () -> executor.invokeAll(asList(
        () -> {
          spanQueue.add(currentSpan.get());
          sleepABit(); // delay here will block the queue
          return true;
        },
        // this won't run immediately because the other is blocked
        () -> spanQueue.add(currentSpan.get())
    )));
  }

  @Test
  public void closeInvokesShutdown() {
    BraveExecutorService.wrap(wrappedExecutor, brave).close();

    assertThat(wrappedExecutor.isShutdown()).isTrue();
  }

  void eachTaskHasCorrectSpanAttached(Supplier<Span> createSpan, Callable<?> scheduleTwoTasks)
      throws Exception {
    Span parent = createSpan.get();

    // First task should block the queue, forcing the latter to not be scheduled immediately
    // Both should have the same parent, as the parent applies to the task creation time, not
    // execution time.
    scheduleTwoTasks.call();

    // switch the current span to something else. If there's a bug, when the
    // second runnable starts, it will have this span as opposed to the one it was
    // invoked with
    createSpan.get();

    assertThat(spanQueue.take()).isEqualTo(parent);
    assertThat(spanQueue.take()).isEqualTo(parent);
  }

  static void sleepABit() {
    try {
      Thread.sleep(500); // intentionally block the executor
    } catch (InterruptedException e) {
    }
  }
}
