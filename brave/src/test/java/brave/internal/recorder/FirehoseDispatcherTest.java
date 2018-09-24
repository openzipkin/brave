package brave.internal.recorder;

import brave.firehose.FirehoseHandler;
import brave.firehose.MutableSpan;
import brave.internal.firehose.FirehoseHandlers;
import brave.propagation.TraceContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FirehoseDispatcherTest {
  List<MutableSpan> spans = new ArrayList<>();
  FirehoseHandler testHandler = new FirehoseHandler() {
    @Override public void handle(TraceContext c, MutableSpan s) {
      spans.add(s);
    }

    @Override public String toString() {
      return "TestFirehoseHandler{}";
    }
  };

  FirehoseDispatcher firehoseDispatcher;

  void init(List<FirehoseHandler.Factory> factories) {
    firehoseDispatcher = new FirehoseDispatcher(factories, "favistar", "1.2.3.4", 0);
  }

  @Test public void noopWhenFactoriesAreEmpty() {
    init(Collections.emptyList());

    assertThat(firehoseDispatcher.firehoseHandler())
        .isSameAs(FirehoseHandler.NOOP);
  }

  @Test public void splitWhenMultipleFirehosePresent() {
    init(Arrays.asList(
        FirehoseHandlers.constantFactory(testHandler),
        FirehoseHandlers.constantFactory(testHandler),
        FirehoseHandlers.constantFactory(testHandler)
    ));

    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
    firehoseDispatcher.firehoseHandler().handle(context, new MutableSpan());

    assertThat(spans).hasSize(3);
  }

  @Test public void doesntCrashOnHandlerError() {
    init(Arrays.asList(FirehoseHandlers.constantFactory((c, s) -> {
      spans.add(s);
      throw new RuntimeException();
    })));

    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
    firehoseDispatcher.firehoseHandler().handle(context, new MutableSpan());

    assertThat(spans).hasSize(1);
  }
}
