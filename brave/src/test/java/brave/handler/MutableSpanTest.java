/*
 * Copyright 2013-2019 The OpenZipkin Authors
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
import brave.propagation.TraceContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class MutableSpanTest {
  /**
   * This is just a dummy pattern. See <a href="https://github.com/ExpediaDotCom/haystack-secrets-commons/blob/master/src/main/java/com/expedia/www/haystack/commons/secretDetector/HaystackCompositeCreditCardFinder.java">HaystackCompositeCreditCardFinder</a>
   * for a realistic one.
   */
  static final Pattern CREDIT_CARD = Pattern.compile("[0-9]{4}-[0-9]{4}-[0-9]{4}-[0-9]{4}");

  /**
   * This shows an edge case of someone implementing a {@link FinishedSpanHandler} whose intent is
   * only handle orphans.
   */
  @Test public void hasAnnotation_usageExplained() {
    class AbandonCounter extends FinishedSpanHandler {
      int orphans;

      @Override public boolean handle(TraceContext context, MutableSpan span) {
        if (span.containsAnnotation("brave.flush")) orphans++;
        return true;
      }

      @Override public boolean supportsOrphans() {
        return true;
      }
    }

    AbandonCounter counter = new AbandonCounter();
    MutableSpan orphan = new MutableSpan();
    orphan.annotate(1, "brave.flush"); // orphaned spans have this annotation

    counter.handle(null, orphan);
    counter.handle(null, new MutableSpan());
    counter.handle(null, orphan);

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
    List<Tag> listTarget = new ArrayList<>();
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

  @Test public void isEmpty() {
    assertThat(new MutableSpan().isEmpty()).isTrue();
    {
      MutableSpan span = new MutableSpan();
      span.name("a");
      assertThat(span.isEmpty()).isFalse();
    }
    {
      MutableSpan span = new MutableSpan();
      span.startTimestamp(1);
      assertThat(span.isEmpty()).isFalse();
    }
    {
      MutableSpan span = new MutableSpan();
      span.finishTimestamp(1);
      assertThat(span.isEmpty()).isFalse();
    }
    {
      MutableSpan span = new MutableSpan();
      span.kind(Span.Kind.CLIENT);
      assertThat(span.isEmpty()).isFalse();
    }
    {
      MutableSpan span = new MutableSpan();
      span.localServiceName("a");
      assertThat(span.isEmpty()).isFalse();
    }
    {
      MutableSpan span = new MutableSpan();
      span.localIp("1.2.3.4");
      assertThat(span.isEmpty()).isFalse();
    }
    {
      MutableSpan span = new MutableSpan();
      span.localPort(1);
      assertThat(span.isEmpty()).isFalse();
    }
    {
      MutableSpan span = new MutableSpan();
      span.remoteServiceName("a");
      assertThat(span.isEmpty()).isFalse();
    }
    {
      MutableSpan span = new MutableSpan();
      span.remoteIpAndPort("1.2.3.4", 1);
      assertThat(span.isEmpty()).isFalse();
    }
    {
      MutableSpan span = new MutableSpan();
      span.annotate(1L, "a");
      assertThat(span.isEmpty()).isFalse();
    }
    {
      MutableSpan span = new MutableSpan();
      span.error(new RuntimeException());
      assertThat(span.isEmpty()).isFalse();
    }
    {
      MutableSpan span = new MutableSpan();
      span.tag("a", "b");
      assertThat(span.isEmpty()).isFalse();
    }
    {
      MutableSpan span = new MutableSpan();
      span.setShared();
      assertThat(span.isEmpty()).isFalse();
    }
  }

  @Test public void accessorScansTags() {
    MutableSpan span = new MutableSpan();
    span.tag("http.method", "GET");
    span.tag("error", "500");
    span.tag("http.path", "/api");

    assertThat(span.tag("error")).isEqualTo("500");
    assertThat(span.tag("whoops")).isNull();
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
