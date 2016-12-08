package com.github.kristofa.brave;

import com.github.kristofa.brave.example.TestServerClientAndLocalSpanStateCompilation;
import com.twitter.zipkin.gen.Span;
import java.util.function.Supplier;
import org.junit.Test;
import zipkin.reporter.Reporter;

import static org.assertj.core.api.Assertions.assertThat;

public class BraveRunnableTest {

  Brave brave = new Brave.Builder(new TestServerClientAndLocalSpanStateCompilation())
      .reporter(Reporter.NOOP)
      .traceSampler(Sampler.ALWAYS_SAMPLE).build();

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

  @Test
  public void attachesSpanInRunnable_deprecatedFactory() throws Exception {
    Span span = createServerSpan.get();
    Runnable runnable =
        BraveRunnable.create(() -> assertThat(currentServerSpan.get()).isEqualTo(span),
            brave.serverSpanThreadBinder());

    // create another span between the time the task was made and executed.
    createServerSpan.get();
    runnable.run(); // runs assertion
  }

  @Test
  public void attachesSpanInRunnable_server() throws Exception {
    attachesSpanInRunnable(createServerSpan, currentServerSpan);
  }

  @Test
  public void attachesSpanInRunnable_local() throws Exception {
    attachesSpanInRunnable(createLocalSpan, currentLocalSpan);
  }

  @Test
  public void restoresSpanAfterRunnable_server() throws Exception {
    Span span = attachesSpanInRunnable(createServerSpan, currentServerSpan);
    assertThat(currentServerSpan.get()).isEqualTo(span);
  }

  @Test
  public void restoresSpanAfterRunnable_local() throws Exception {
    Span span = attachesSpanInRunnable(createLocalSpan, currentLocalSpan);
    assertThat(currentLocalSpan.get()).isEqualTo(span);
  }

  Span attachesSpanInRunnable(Supplier<Span> createSpan, Supplier<Span> currentSpan)
      throws Exception {
    Span span = createSpan.get();
    Runnable runnable =
        BraveRunnable.wrap(() -> assertThat(currentSpan.get()).isEqualTo(span), brave);

    // create another span between the time the task was made and executed.
    Span nextSpan = createSpan.get();

    runnable.run(); // runs assertion

    return nextSpan;
  }
}
