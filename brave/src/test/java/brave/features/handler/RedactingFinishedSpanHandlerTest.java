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
package brave.features.handler;

import brave.ScopedSpan;
import brave.Tracing;
import brave.handler.FinishedSpanHandler;
import brave.handler.MutableSpan;
import brave.handler.MutableSpan.AnnotationUpdater;
import brave.handler.MutableSpan.TagUpdater;
import brave.propagation.TraceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.After;
import org.junit.Test;
import zipkin2.Annotation;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/** One reason {@link brave.handler.MutableSpan} is mutable is to support redaction */
public class RedactingFinishedSpanHandlerTest {
  static final Pattern SSN = Pattern.compile("[0-9]{3}-[0-9]{2}-[0-9]{4}");

  enum ValueRedactor implements TagUpdater, AnnotationUpdater {
    INSTANCE;

    @Override public String update(String key, String value) {
      return maybeUpdateValue(value);
    }

    @Override public String update(long timestamp, String value) {
      return maybeUpdateValue(value);
    }

    /** Simple example of a replacement pattern, deleting entries which only include SSNs */
    static String maybeUpdateValue(String value) {
      Matcher matcher = SSN.matcher(value);
      if (matcher.find()) {
        String matched = matcher.group(0);
        if (matched.equals(value)) return null;
        return value.replace(matched, "xxx-xx-xxxx");
      }
      return value;
    }
  }

  List<Span> spans = new ArrayList<>();
  Tracing tracing = Tracing.newBuilder()
    .addFinishedSpanHandler(new FinishedSpanHandler() {
      @Override public boolean handle(TraceContext context, MutableSpan span) {
        span.forEachTag(ValueRedactor.INSTANCE);
        span.forEachAnnotation(ValueRedactor.INSTANCE);
        return true;
      }
    })
    .spanReporter(spans::add)
    .build();

  @After public void close() {
    tracing.close();
  }

  @Test public void showRedaction() {
    ScopedSpan span = tracing.tracer().startScopedSpan("auditor");
    try {
      span.tag("a", "1");
      span.tag("b", "912-23-1433");
      span.annotate("SSN=912-23-1433");
      span.tag("c", "3");
    } finally {
      span.finish();
    }

    assertThat(spans.get(0).tags()).containsExactly(
      entry("a", "1"),
      // SSN tag was nuked
      entry("c", "3")
    );
    assertThat(spans.get(0).annotations()).flatExtracting(Annotation::value).containsExactly(
      "SSN=xxx-xx-xxxx"
    );
  }
}
