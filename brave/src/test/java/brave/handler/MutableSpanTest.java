/*
 * Copyright 2013-2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.handler;

import brave.Span;
import brave.internal.InternalPropagation;
import brave.propagation.TraceContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class MutableSpanTest {
  /**
   * This is just a dummy pattern. See <a href="https://github.com/ExpediaDotCom/haystack-secrets-commons/blob/master/src/main/java/com/expedia/www/haystack/commons/secretDetector/HaystackCompositeCreditCardFinder.java">HaystackCompositeCreditCardFinder</a>
   * for a realistic one.
   */
  static final Pattern CREDIT_CARD = Pattern.compile("[0-9]{4}-[0-9]{4}-[0-9]{4}-[0-9]{4}");

  /**
   * This shows an edge case of someone implementing a {@link SpanHandler} whose intent is
   * only handle orphans.
   */
  @Test public void hasAnnotation_usageExplained() {
    class AbandonCounter extends SpanHandler {
      int orphans;

      @Override public boolean end(TraceContext context, MutableSpan span, Cause cause) {
        if (cause == Cause.ORPHANED) orphans++;
        return true;
      }
    }

    AbandonCounter counter = new AbandonCounter();
    MutableSpan orphan = new MutableSpan();

    counter.end(null, orphan, SpanHandler.Cause.ORPHANED);
    counter.end(null, new MutableSpan(), SpanHandler.Cause.FLUSHED);
    counter.end(null, orphan, SpanHandler.Cause.ORPHANED);

    assertThat(counter.orphans).isEqualTo(2);
  }

  /** This is a compile test to show how the signature is intended to be used */
  @Test public void forEachTag_consumer_usageExplained() {
    MutableSpan span = new MutableSpan();
    span.tag("a", "1");
    span.tag("b", "2");
    span.tag("c", "3");

    // Similar to micrometer metrics tags
    class Tag {
      final String name, value;

      Tag(String name, String value) {
        this.name = name;
        this.value = value;
      }

      @Override public boolean equals(Object o) {
        if (!(o instanceof Tag)) return false;
        Tag that = (Tag) o;
        return name.equals(that.name) && value.equals(that.value);
      }
    }

    // When exporting into a list, a lambda would usually need to close over the list, which results
    // in a new instance per invocation. Since there's a target type parameter, the lambda for this
    // style of conversion can be constant, reducing overhead.
    List<Tag> listTarget = new ArrayList<>(span.tagCount());
    span.forEachTag((target, key, value) -> target.add(new Tag(key, value)), listTarget);

    assertThat(listTarget).containsExactly(
      new Tag("a", "1"),
      new Tag("b", "2"),
      new Tag("c", "3")
    );
  }

  /** This is a compile test to show how the signature is intended to be used */
  @Test public void forEachTag_updater_usageExplained() {
    MutableSpan span = new MutableSpan();
    span.tag("a", "1");
    span.tag("cc", "4121-2319-1483-3421");
    span.tag("cc-suffix", "cc=4121-2319-1483-3421");
    span.tag("c", "3");

    // The lambda here can be a constant as it doesn't need to inspect anything.
    // Also, it doesn't have to loop twice to remove data.
    span.forEachTag((key, value) -> {
      Matcher matcher = CREDIT_CARD.matcher(value);
      if (matcher.find()) {
        String matched = matcher.group(0);
        if (matched.equals(value)) return null;
        return value.replace(matched, "xxxx-xxxx-xxxx-xxxx");
      }
      return value;
    });

    assertThat(tagsToMap(span)).containsExactly(
      entry("a", "1"),
      entry("cc-suffix", "cc=xxxx-xxxx-xxxx-xxxx"),
      entry("c", "3")
    );
  }

  @Test public void annotationCount() {
    MutableSpan span = new MutableSpan();
    assertThat(span.annotationCount()).isZero();
    span.annotate(1L, "1");
    assertThat(span.annotationCount()).isEqualTo(1);
    span.annotate(2L, "2");
    assertThat(span.annotationCount()).isEqualTo(2);
    span.forEachAnnotation((t, v) -> v.equals("1") ? v : null);
    assertThat(span.annotationCount()).isEqualTo(1);
  }

  /** See {@link #forEachTag_consumer_usageExplained()} */
  @Test public void forEachAnnotation_consumer_usageExplained() {
    TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(2L).build();

    MutableSpan span = new MutableSpan();
    span.annotate(1L, "1");
    span.annotate(2L, "2");
    span.annotate(2L, "2-1");
    span.annotate(3L, "3");

    // Some may want to export data to their logging system under a trace ID/Timestamp
    // While the syntax here isn't precise, it is similar to what one can do with a firehose
    // handler which receives (context, span) inputs.
    Logger logger = Logger.getLogger(getClass().getName());
    span.forEachAnnotation((target, timestamp, value) -> {
      LogRecord record = new LogRecord(Level.FINE, value);
      record.setParameters(
        new Object[] {context.traceIdString(), context.spanIdString()});
      record.setMillis(timestamp / 1000L);
      target.log(record);
    }, logger);
  }

  /** See {@link #forEachTag_updater_usageExplained()} */
  @Test public void forEachAnnotation_updater_usageExplained() {
    MutableSpan span = new MutableSpan();
    span.annotate(1L, "1");
    span.annotate(2L, "4121-2319-1483-3421");
    span.annotate(2L, "cc=4121-2319-1483-3421");
    span.annotate(3L, "3");

    // The lambda here can be a constant as it doesn't need to inspect anything.
    // Also, it doesn't have to loop twice to remove data.
    span.forEachAnnotation((key, value) -> {
      Matcher matcher = CREDIT_CARD.matcher(value);
      if (matcher.find()) {
        String matched = matcher.group(0);
        if (matched.equals(value)) return null;
        return value.replace(matched, "xxxx-xxxx-xxxx-xxxx");
      }
      return value;
    });

    assertThat(annotationsToList(span)).containsExactly(
      entry(1L, "1"),
      entry(2L, "cc=xxxx-xxxx-xxxx-xxxx"),
      entry(3L, "3")
    );
  }

  @Test public void localServiceNamePreservesCase() {
    String expectedLocalServiceName = "FavStar";
    MutableSpan span = new MutableSpan();
    span.localServiceName(expectedLocalServiceName);
    assertThat(span.localServiceName()).isEqualTo(expectedLocalServiceName);
  }

  @Test public void remoteServiceNamePreservesCase() {
    String expectedRemoteServiceName = "FavStar";
    MutableSpan span = new MutableSpan();
    span.remoteServiceName(expectedRemoteServiceName);
    assertThat(span.remoteServiceName()).isEqualTo(expectedRemoteServiceName);
  }

  /**
   * {@link brave.Span#kind(Span.Kind)} is nullable, so setting kind to null should work.
   *
   * <p>This allows you to change the decision later if a span is not remote, for example, when
   * served from cache.
   */
  @Test public void unsetKind() {
    MutableSpan span = new MutableSpan();
    span.kind(Span.Kind.CLIENT);
    span.kind(null);

    assertThat(span.kind()).isNull();
  }

  @Test public void isEmpty() {
    assertThat(permutations.get(0).get().isEmpty()).isTrue();

    for (int i = 1, length = permutations.size(); i < length; i++) {
      assertThat(permutations.get(i).get().isEmpty()).isFalse();
    }
  }

  static final Exception EX1 = new Exception(), EX2 = new Exception();

  @Test public void equalsOnHashCodeClash() {
    // Not as good as property testing, but easier to see changes later when fields are added!
    List<Function<String, MutableSpan>> permutations = asList(
      string -> {
        MutableSpan span = new MutableSpan();
        span.name(string);
        return span;
      },
      string -> {
        MutableSpan span = new MutableSpan();
        span.localServiceName(string);
        return span;
      },
      string -> {
        MutableSpan span = new MutableSpan();
        span.remoteServiceName(string);
        return span;
      },
      string -> {
        MutableSpan span = new MutableSpan();
        span.tag(string, "");
        return span;
      },
      string -> {
        MutableSpan span = new MutableSpan();
        span.tag("error", string);
        return span;
      },
      string -> {
        MutableSpan span = new MutableSpan();
        span.annotate(1L, string);
        return span;
      }
      // TODO: find two IPv6 literals whose string forms clash on hashCode
      // bonus if there are actually IPv4 literals that clash on hashCode
    );

    for (Function<String, MutableSpan> factory : permutations) {
      MutableSpan Aa = factory.apply("Aa");
      MutableSpan BB = factory.apply("BB");
      assertThat(Aa)
        .isNotEqualTo(BB)
        .extracting(MutableSpan::hashCode)
        .isEqualTo(BB.hashCode()); // clash
    }
  }

  // Not as good as property testing, but easier to see changes later when fields are added!
  List<Supplier<MutableSpan>> permutations = asList(
    MutableSpan::new,
    () -> {
      MutableSpan span = new MutableSpan();
      span.traceId("a");
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.traceId("b");
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.localRootId("a");
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.localRootId("b");
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.parentId("a");
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.parentId("b");
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.id("a");
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.id("b");
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.setDebug();
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.setShared();
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.kind(Span.Kind.CLIENT);
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.kind(Span.Kind.SERVER);
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.startTimestamp(1L);
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.startTimestamp(2L);
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.finishTimestamp(1L);
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.finishTimestamp(2L);
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.name("foo");
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.name("Foo");
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.localServiceName("foo");
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.localServiceName("Foo");
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.localIp("1.2.3.4");
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.localIp("::1");
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.localPort(80);
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.localPort(443);
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.remoteServiceName("foo");
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.remoteServiceName("Foo");
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.remoteIpAndPort("1.2.3.4", 0);
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.remoteIpAndPort("::1", 0);
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.remoteIpAndPort("127.0.0.1", 80);
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.remoteIpAndPort("127.0.0.1", 443);
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.tag("error", "wasted");
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.tag("error", "");
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.annotate(1L, "wasted");
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.annotate(2L, "wasted");
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.error(EX1);
      return span;
    },
    () -> {
      MutableSpan span = new MutableSpan();
      span.error(EX2);
      return span;
    }
  );

  @Test public void equalsAndHashCode() {
    for (Supplier<MutableSpan> constructor : permutations) {
      // same instance are equivalent
      MutableSpan span = constructor.get();
      assertThat(span).isEqualTo(span);
      assertThat(span).hasSameHashCodeAs(span);

      // same fields are equivalent
      assertThat(span).isEqualTo(constructor.get());
      assertThat(span).hasSameHashCodeAs(constructor.get());

      // This seems redundant, and mostly is, but the order of equals matters
      List<Supplier<MutableSpan>> exceptMe = new ArrayList<>(permutations);
      exceptMe.remove(constructor);
      for (Supplier<MutableSpan> otherConstructor : exceptMe) {
        MutableSpan other = otherConstructor.get();
        assertThat(span)
          .isNotSameAs(other) // sanity
          .isNotEqualTo(other)
          .extracting(MutableSpan::hashCode)
          .isNotEqualTo(other.hashCode());
      }
    }
  }

  @Test public void copyConstructor() {
    for (Supplier<MutableSpan> constructor : permutations) {
      MutableSpan span = constructor.get();
      assertThat(span).isEqualTo(new MutableSpan(span));
    }
  }

  @Test public void contextConstructor() {
    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).build();
    MutableSpan span = new MutableSpan();
    span.traceId("0000000000000001");
    span.id("0000000000000002");
    assertThat(new MutableSpan(context, null)).isEqualTo(span);

    // local root ID is not a public api
    context = InternalPropagation.instance.newTraceContext(
      0,
      0,
      1,
      2,
      3,
      4,
      emptyList()
    );
    span.traceId("0000000000000001");
    span.localRootId("0000000000000002");
    span.parentId("0000000000000003");
    span.id("0000000000000004");
    assertThat(new MutableSpan(context, null)).isEqualTo(span);

    context = context.toBuilder().shared(true).build();
    span.setShared();
    assertThat(new MutableSpan(context, null)).isEqualTo(span);

    context = context.toBuilder().debug(true).build();
    span.setDebug();
    assertThat(new MutableSpan(context, null)).isEqualTo(span);
  }

  @Test public void contextConstructor_contextWins() {
    MutableSpan span = new MutableSpan();
    span.traceId("0000000000000001");
    span.localRootId("0000000000000002");
    span.parentId("0000000000000003");
    span.id("0000000000000004");
    span.setShared();
    span.setDebug();

    TraceContext context = TraceContext.newBuilder().traceId(10).spanId(20).build();

    assertThat(new MutableSpan(context, span))
      .isEqualTo(new MutableSpan(context, null));
  }

  @Test public void tagCount() {
    MutableSpan span = new MutableSpan();
    assertThat(span.tagCount()).isZero();
    span.tag("http.method", "GET");
    assertThat(span.tagCount()).isEqualTo(1);
    span.tag("error", "500");
    assertThat(span.tagCount()).isEqualTo(2);
    span.forEachTag((t, v) -> v.equals("GET") ? v : null);
    assertThat(span.tagCount()).isEqualTo(1);
  }

  @Test public void accessorScansTags() {
    MutableSpan span = new MutableSpan();
    span.tag("http.method", "GET");
    span.tag("error", "500");
    span.tag("http.path", "/api");

    assertThat(span.tag("error")).isEqualTo("500");
    assertThat(span.tag("whoops")).isNull();
  }

  @Test public void toString_testCases() {
    assertThat(permutations.get(0).get()).hasToString("{}");

    // check for simple bugs
    for (int i = 1, length = permutations.size(); i < length; i++) {
      assertThat(permutations.get(i).get().toString())
        .doesNotContain("null")
        .doesNotContain(":0");
    }

    // now, test something more interesting zipkin2.TestObjects.CLIENT_SPAN
    MutableSpan span = new MutableSpan();
    span.traceId("1");
    span.localRootId("2"); // not in zipkin format
    span.parentId("2");
    span.id("2");
    span.name("get");
    span.kind(Span.Kind.CLIENT);
    span.localServiceName("frontend");
    span.localIp("127.0.0.1");
    span.remoteServiceName("backend");
    span.remoteIpAndPort("192.168.99.101", 9000);
    span.startTimestamp(1000L);
    span.finishTimestamp(1200L);
    span.annotate(1100L, "foo");
    span.tag("http.path", "/api");
    span.tag("clnt/finagle.version", "6.45.0");

    assertThat(span).hasToString("{"
      + "\"traceId\":\"1\",\"parentId\":\"2\",\"id\":\"2\","
      + "\"kind\":\"CLIENT\",\"name\":\"get\",\"timestamp\":1000,\"duration\":200,"
      + "\"localEndpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"},"
      + "\"remoteEndpoint\":{\"serviceName\":\"backend\",\"ipv4\":\"192.168.99.101\",\"port\":9000},"
      + "\"annotations\":[{\"timestamp\":1100,\"value\":\"foo}],"
      + "\"tags\":{\"http.path\":\"/api\",\"clnt/finagle.version\":\"6.45.0\"}"
      + "}");
  }

  static Map<String, String> tagsToMap(MutableSpan span) {
    Map<String, String> map = new LinkedHashMap<>();
    span.forEachTag(Map::put, map);
    return map;
  }

  static List<Map.Entry<Long, String>> annotationsToList(MutableSpan span) {
    List<Map.Entry<Long, String>> listTarget = new ArrayList<>();
    span.forEachAnnotation((target, key, value) -> target.add(entry(key, value)), listTarget);
    return listTarget;
  }
}
