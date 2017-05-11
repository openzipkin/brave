package com.github.kristofa.brave;

import com.github.kristofa.brave.example.TestServerClientAndLocalSpanStateCompilation;
import com.twitter.zipkin.gen.Span;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import org.junit.Test;
import zipkin.reporter.Reporter;

import static org.assertj.core.api.Assertions.assertThat;

public class BraveCallableTest {

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
  public void attachesSpanInCallable_deprecatedFactory() throws Exception {
    Span span = createServerSpan.get();
    Callable<?> callable =
        BraveCallable.create(() -> assertThat(currentServerSpan.get()).isEqualTo(span),
            brave.serverSpanThreadBinder());

    // create another span between the time the task was made and executed.
    createServerSpan.get();
    callable.call(); // runs assertion
  }

  @Test
  public void attachesSpanInCallable_server() throws Exception {
    attachesSpanInCallable(createServerSpan, currentServerSpan);
  }

  @Test
  public void attachesSpanInCallable_local() throws Exception {
    attachesSpanInCallable(createLocalSpan, currentLocalSpan);
  }

  @Test
  public void restoresSpanAfterCallable_server() throws Exception {
    Span span = attachesSpanInCallable(createServerSpan, currentServerSpan);
    assertThat(currentServerSpan.get()).isEqualTo(span);
  }

  @Test
  public void restoresSpanAfterCallable_local() throws Exception {
    Span span = attachesSpanInCallable(createLocalSpan, currentLocalSpan);
    assertThat(currentLocalSpan.get()).isEqualTo(span);
  }

  Span attachesSpanInCallable(Supplier<Span> createSpan, Supplier<Span> currentSpan)
      throws Exception {
    Span span = createSpan.get();
    Callable<?> callable =
        BraveCallable.wrap(() -> assertThat(currentSpan.get()).isEqualTo(span), brave);

    // create another span between the time the task was made and executed.
    Span nextSpan = createSpan.get();

    callable.call(); // runs assertion

    return nextSpan;
  }
}
