package brave.handler;

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
  static final Pattern SSN = Pattern.compile("[0-9]{3}\\-[0-9]{2}\\-[0-9]{4}");

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
    span.tag("ssn", "912-23-1433");
    span.tag("ssn-suffix", "SSN=912-23-1433");
    span.tag("c", "3");

    // The lambda here can be a constant as it doesn't need to inspect anything.
    // Also, it doesn't have to loop twice to remove data.
    span.forEachTag((key, value) -> {
      Matcher matcher = SSN.matcher(value);
      if (matcher.find()) {
        String matched = matcher.group(0);
        if (matched.equals(value)) return null;
        return value.replace(matched, "xxx-xx-xxxx");
      }
      return value;
    });

    assertThat(tagsToMap(span)).containsExactly(
        entry("a", "1"),
        entry("ssn-suffix", "SSN=xxx-xx-xxxx"),
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
    span.annotate(2L, "912-23-1433");
    span.annotate(2L, "SSN=912-23-1433");
    span.annotate(3L, "3");

    // The lambda here can be a constant as it doesn't need to inspect anything.
    // Also, it doesn't have to loop twice to remove data.
    span.forEachAnnotation((key, value) -> {
      Matcher matcher = SSN.matcher(value);
      if (matcher.find()) {
        String matched = matcher.group(0);
        if (matched.equals(value)) return null;
        return value.replace(matched, "xxx-xx-xxxx");
      }
      return value;
    });

    assertThat(annotationsToList(span)).containsExactly(
        entry(1L, "1"),
        entry(2L, "SSN=xxx-xx-xxxx"),
        entry(3L, "3")
    );
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
