package brave.internal.firehose;

import brave.firehose.FirehoseHandler;
import brave.firehose.MutableSpan;
import brave.propagation.TraceContext;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class FirehoseHandlersTest {
  FirehoseHandler one = new FirehoseHandler() {
    @Override public void handle(TraceContext context, MutableSpan span) {
      span.tag("one", "");
    }

    @Override public String toString() {
      return "one";
    }
  };
  FirehoseHandler two = new FirehoseHandler() {
    @Override public void handle(TraceContext context, MutableSpan span) {
      span.tag("two", "");
    }

    @Override public String toString() {
      return "two";
    }
  };
  FirehoseHandler three = new FirehoseHandler() {
    @Override public void handle(TraceContext context, MutableSpan span) {
      span.tag("three", "");
    }

    @Override public String toString() {
      return "three";
    }
  };

  @Test public void create_emptyToEmpty() {
    assertThat(FirehoseHandlers.create(asList(), "favistar", "1.2.3.4", 80))
        .isSameAs(Collections.emptyList());
  }

  @Test public void create_passesLocalEndpointArgs() {
    FirehoseHandler.Factory oneFactory = new FirehoseHandler.Factory() {
      @Override public FirehoseHandler create(String serviceName, String ip, int port) {
        assertThat(serviceName).isEqualTo("favistar");
        assertThat(ip).isEqualTo("1.2.3.4");
        assertThat(port).isEqualTo(80);
        return one;
      }
    };

    FirehoseHandlers.create(asList(oneFactory), "favistar", "1.2.3.4", 80);
  }

  @Test public void create_singletonList() {
    FirehoseHandler.Factory oneFactory = FirehoseHandlers.constantFactory(one);

    assertThat(FirehoseHandlers.create(asList(oneFactory), "favistar", "1.2.3.4", 80))
        .containsExactly(one);
  }

  @Test public void create_invokesInOrder() {
    FirehoseHandler.Factory noopFactory = FirehoseHandlers.constantFactory(FirehoseHandler.NOOP);
    FirehoseHandler.Factory oneFactory = FirehoseHandlers.constantFactory(one);
    FirehoseHandler.Factory twoFactory = FirehoseHandlers.constantFactory(two);
    FirehoseHandler.Factory threeFactory = FirehoseHandlers.constantFactory(three);

    assertThat(
        FirehoseHandlers.create(asList(oneFactory, twoFactory, threeFactory), "favistar", null, 0)
    ).containsExactly(one, two, three);
    assertThat(
        FirehoseHandlers.create(asList(noopFactory, twoFactory), "favistar", null, 0)
    ).containsExactly(two);
    assertThat(
        FirehoseHandlers.create(asList(noopFactory, twoFactory, threeFactory), "favistar", null, 0)
    ).containsExactly(two, three);
    assertThat(
        FirehoseHandlers.create(asList(oneFactory, noopFactory, threeFactory), "favistar", null, 0)
    ).containsExactly(one, three);
    assertThat(
        FirehoseHandlers.create(asList(oneFactory, twoFactory, noopFactory), "favistar", null, 0)
    ).containsExactly(one, two);
  }

  @Test public void compose_emptyToNoop() {
    assertThat(FirehoseHandlers.compose(asList())).isSameAs(FirehoseHandler.NOOP);
  }

  @Test public void compose_singleToInstance() {
    assertThat(FirehoseHandlers.compose(asList(one))).isSameAs(one);
  }

  @Test public void compose_skipsNoop() {
    assertThat(FirehoseHandlers.compose(asList(FirehoseHandler.NOOP)))
        .isSameAs(FirehoseHandler.NOOP);

    {
      MutableSpan span = new MutableSpan();
      FirehoseHandlers.compose(asList(FirehoseHandler.NOOP, two)).handle(null, span);

      Map<String, String> tags = new LinkedHashMap<>();
      span.forEachTag(Map::put, tags);
      assertThat(tags).containsKeys("two");
    }
    {
      MutableSpan span = new MutableSpan();
      FirehoseHandlers.compose(asList(FirehoseHandler.NOOP, two, three)).handle(null, span);

      Map<String, String> tags = new LinkedHashMap<>();
      span.forEachTag(Map::put, tags);
      assertThat(tags).containsKeys("two", "three");
    }
    {
      MutableSpan span = new MutableSpan();
      FirehoseHandlers.compose(asList(one, FirehoseHandler.NOOP, three)).handle(null, span);

      Map<String, String> tags = new LinkedHashMap<>();
      span.forEachTag(Map::put, tags);
      assertThat(tags).containsKeys("one", "three");
    }
    {
      MutableSpan span = new MutableSpan();
      FirehoseHandlers.compose(asList(one, two, FirehoseHandler.NOOP)).handle(null, span);

      Map<String, String> tags = new LinkedHashMap<>();
      span.forEachTag(Map::put, tags);
      assertThat(tags).containsKeys("one", "two");
    }
  }

  @Test public void compose_invokesInOrder() {
    MutableSpan span = new MutableSpan();
    FirehoseHandlers.compose(asList(one, two, three)).handle(null, span);

    Map<String, String> tags = new LinkedHashMap<>();
    span.forEachTag(Map::put, tags);
    assertThat(tags).containsKeys("one", "two", "three");
  }

  @Test public void compose_hasNiceToString() {
    assertThat(FirehoseHandlers.compose(asList(one)))
        .hasToString("one");
    assertThat(FirehoseHandlers.compose(asList(one, two)))
        .hasToString("CompositeFirehoseHandler(one,two)");
    assertThat(FirehoseHandlers.compose(asList(one, two, three)))
        .hasToString("CompositeFirehoseHandler(one,two,three)");
  }

  @Test public void noopAware_doesntCrashOnException() {
    FirehoseHandlers.noopAware((c, s) -> {
      throw new RuntimeException();
    }, new AtomicBoolean()).handle(null, new MutableSpan());
  }

  @Test public void noopAware_doesntHandleWhenNoop() {
    AtomicReference<MutableSpan> mutableSpanRef = new AtomicReference<>();
    AtomicBoolean noop = new AtomicBoolean();

    FirehoseHandler handler = FirehoseHandlers.noopAware((c, s) -> mutableSpanRef.set(s), noop);

    handler.handle(null, new MutableSpan());
    assertThat(mutableSpanRef.getAndSet(null)).isNotNull();

    noop.set(true);

    handler.handle(null, new MutableSpan());
    assertThat(mutableSpanRef.getAndSet(null)).isNull();
  }
}
