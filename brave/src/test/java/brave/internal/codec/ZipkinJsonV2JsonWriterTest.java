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
package brave.internal.codec;

import brave.Span;
import brave.Tags;
import brave.handler.MutableSpan;
import brave.handler.MutableSpanTest;
import org.junit.Before;
import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class ZipkinJsonV2JsonWriterTest {
  ZipkinV2JsonWriter jsonWriter = new ZipkinV2JsonWriter(Tags.ERROR);
  WriteBuffer buffer = new WriteBuffer(new byte[512], 0);

  MutableSpan clientSpan = new MutableSpan();

  @Before public void createClientSpan() {
    clientSpan.traceId("1"); // note: we didn't pad here.. it is done implicitly
    clientSpan.localRootId("2"); // not a zipkin v2 field
    clientSpan.parentId("2");
    clientSpan.id("3");
    clientSpan.name("get");
    clientSpan.kind(Span.Kind.CLIENT);
    clientSpan.localServiceName("frontend");
    clientSpan.localIp("127.0.0.1");
    clientSpan.remoteServiceName("backend");
    clientSpan.remoteIpAndPort("192.168.99.101", 9000);
    clientSpan.startTimestamp(1000L);
    clientSpan.finishTimestamp(1200L);
    clientSpan.annotate(1100L, "foo");
    clientSpan.tag("http.path", "/api");
    clientSpan.tag("clnt/finagle.version", "6.45.0");
  }

  @Test public void sizeInBytes_matchesWhatsWritten() {
    assertThat(jsonWriter.sizeInBytes(MutableSpanTest.PERMUTATIONS.get(0).get()))
      .isEqualTo(2); // {}

    // check for simple bugs
    for (int i = 1, length = MutableSpanTest.PERMUTATIONS.size(); i < length; i++) {
      buffer.pos = 0;
      MutableSpan span = MutableSpanTest.PERMUTATIONS.get(i).get();

      jsonWriter.write(span, buffer);
      int size = jsonWriter.sizeInBytes(span);
      assertThat(jsonWriter.sizeInBytes(span))
        .withFailMessage("expected to write %s bytes: was %s for %s", size, buffer.pos, span)
        .isEqualTo(buffer.pos);
    }
  }

  @Test public void specialCharacters() {
    MutableSpan span = new MutableSpan();
    span.name("\u2028 and \u2029");
    span.localServiceName("\"foo");
    span.tag("hello \n", "\t\b");
    span.annotate(1L, "\uD83D\uDCA9");

    jsonWriter.write(span, buffer);
    String string = buffer.toString();
    assertThat(string)
      .isEqualTo(
        "{\"name\":\"\\u2028 and \\u2029\",\"localEndpoint\":{\"serviceName\":\"\\\"foo\"},\"annotations\":[{\"timestamp\":1,\"value\":\"\uD83D\uDCA9\"}],\"tags\":{\"hello \\n\":\"\\t\\b\"}}");

    assertThat(jsonWriter.sizeInBytes(span))
      .isEqualTo(string.getBytes(UTF_8).length);
  }

  @Test public void missingFields_testCases() {
    jsonWriter.write(MutableSpanTest.PERMUTATIONS.get(0).get(), buffer);
    assertThat(buffer.toString()).isEqualTo("{}");

    // check for simple bugs
    for (int i = 1, length = MutableSpanTest.PERMUTATIONS.size(); i < length; i++) {
      buffer.pos = 0;

      MutableSpan span = MutableSpanTest.PERMUTATIONS.get(i).get();
      jsonWriter.write(span, buffer);

      assertThat(buffer.toString())
        .doesNotContain("null")
        .doesNotContain(":0");
    }
  }

  @Test public void writeClientSpan() {
    jsonWriter.write(clientSpan, buffer);

    assertThat(buffer.toString()).isEqualTo("{"
      + "\"traceId\":\"0000000000000001\",\"parentId\":\"0000000000000002\",\"id\":\"0000000000000003\","
      + "\"kind\":\"CLIENT\",\"name\":\"get\",\"timestamp\":1000,\"duration\":200,"
      + "\"localEndpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"},"
      + "\"remoteEndpoint\":{\"serviceName\":\"backend\",\"ipv4\":\"192.168.99.101\",\"port\":9000},"
      + "\"annotations\":[{\"timestamp\":1100,\"value\":\"foo\"}],"
      + "\"tags\":{\"http.path\":\"/api\",\"clnt/finagle.version\":\"6.45.0\"}"
      + "}");
  }
}
