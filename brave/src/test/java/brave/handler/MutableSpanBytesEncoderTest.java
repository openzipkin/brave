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

import brave.Span.Kind;
import brave.Tags;
import org.junit.Before;
import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * This test is intentionally sensitive to ensure our custom encoders do not break in subtle ways.
 */
// Originally, a subset of zipkin2.code.SpanBytesEncoderTest 
public class MutableSpanBytesEncoderTest {
  MutableSpan
      clientSpan = new MutableSpan(),
      rootServerSpan = new MutableSpan(),
      localSpan = new MutableSpan(),
      errorSpan = new MutableSpan(),
      utf8Span = new MutableSpan();

  MutableSpanBytesEncoder encoder = MutableSpanBytesEncoder.zipkinJsonV2(Tags.ERROR);

  @Before public void testData() {
    clientSpan.traceId("7180c278b62e8f6a216a2aea45d08fc9");
    clientSpan.parentId("6b221d5bc9e6496c");
    clientSpan.id("5b4185666d50f68b");
    clientSpan.name("get");
    clientSpan.kind(Kind.CLIENT);
    clientSpan.localServiceName("frontend");
    clientSpan.localIp("127.0.0.1");
    clientSpan.remoteServiceName("backend");
    clientSpan.remoteIpAndPort("192.168.99.101", 9000);
    clientSpan.startTimestamp(1472470996199000L);
    clientSpan.finishTimestamp(1472470996199000L + 207000L);
    clientSpan.annotate(1472470996238000L, "foo");
    clientSpan.annotate(1472470996403000L, "bar");
    clientSpan.tag("clnt/finagle.version", "6.45.0");
    clientSpan.tag("http.path", "/api");

    rootServerSpan.traceId("dc955a1d4768875d");
    rootServerSpan.id("dc955a1d4768875d");
    rootServerSpan.name("get");
    rootServerSpan.startTimestamp(1510256710021866L);
    rootServerSpan.finishTimestamp(1510256710021866L + 1117L);
    rootServerSpan.kind(Kind.SERVER);
    rootServerSpan.localServiceName("isao01");
    rootServerSpan.localIp("10.23.14.72");
    rootServerSpan.tag("http.path", "/rs/A");
    rootServerSpan.tag("location", "T67792");
    rootServerSpan.tag("other", "A");

    localSpan.traceId("dc955a1d4768875d");
    localSpan.id("dc955a1d4768875d");
    localSpan.name("encode");
    localSpan.startTimestamp(1510256710021866L);
    localSpan.finishTimestamp(1510256710021866L + 1117L);
    localSpan.localServiceName("isao01");
    localSpan.localIp("10.23.14.72");

    // the following skeletal span is used in dependency linking
    errorSpan.traceId("dc955a1d4768875d");
    errorSpan.id("dc955a1d4768875d");
    errorSpan.localServiceName("isao01");
    errorSpan.kind(Kind.CLIENT);
    errorSpan.tag("error", "");

    // service name is surrounded by control characters
    utf8Span.traceId("1");
    utf8Span.id("1");
    utf8Span.name("get");
    clientSpan.kind(Kind.CLIENT);
    // name is terrible
    utf8Span.name(new String(new char[] {'"', '\\', '\t', '\b', '\n', '\r', '\f'}));
    // annotation value includes some json newline characters
    utf8Span.annotate(1L, "\u2028 and \u2029");
    // tag key includes a quote and value newlines
    utf8Span.tag("\"foo",
        "Database error: ORA-00942:\u2028 and \u2029 table or view does not exist\n");
  }

  @Test public void span_JSON_V2() {
    assertThat(new String(encoder.encode(clientSpan), UTF_8))
        .isEqualTo(
            "{\"traceId\":\"7180c278b62e8f6a216a2aea45d08fc9\",\"parentId\":\"6b221d5bc9e6496c\",\"id\":\"5b4185666d50f68b\",\"kind\":\"CLIENT\",\"name\":\"get\",\"timestamp\":1472470996199000,\"duration\":207000,\"localEndpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"},\"remoteEndpoint\":{\"serviceName\":\"backend\",\"ipv4\":\"192.168.99.101\",\"port\":9000},\"annotations\":[{\"timestamp\":1472470996238000,\"value\":\"foo\"},{\"timestamp\":1472470996403000,\"value\":\"bar\"}],\"tags\":{\"clnt/finagle.version\":\"6.45.0\",\"http.path\":\"/api\"}}");
  }

  @Test public void localSpan_JSON_V2() {
    assertThat(new String(encoder.encode(localSpan), UTF_8))
        .isEqualTo(
            "{\"traceId\":\"dc955a1d4768875d\",\"id\":\"dc955a1d4768875d\",\"name\":\"encode\",\"timestamp\":1510256710021866,\"duration\":1117,\"localEndpoint\":{\"serviceName\":\"isao01\",\"ipv4\":\"10.23.14.72\"}}");
  }

  @Test public void errorSpan_JSON_V2() {
    assertThat(new String(encoder.encode(errorSpan), UTF_8))
        .isEqualTo(
            "{\"traceId\":\"dc955a1d4768875d\",\"id\":\"dc955a1d4768875d\",\"kind\":\"CLIENT\",\"localEndpoint\":{\"serviceName\":\"isao01\"},\"tags\":{\"error\":\"\"}}");
  }

  @Test public void span_64bitTraceId_JSON_V2() {
    clientSpan.traceId(clientSpan.traceId().substring(16));

    assertThat(new String(encoder.encode(clientSpan), UTF_8))
        .isEqualTo(
            "{\"traceId\":\"216a2aea45d08fc9\",\"parentId\":\"6b221d5bc9e6496c\",\"id\":\"5b4185666d50f68b\",\"kind\":\"CLIENT\",\"name\":\"get\",\"timestamp\":1472470996199000,\"duration\":207000,\"localEndpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"},\"remoteEndpoint\":{\"serviceName\":\"backend\",\"ipv4\":\"192.168.99.101\",\"port\":9000},\"annotations\":[{\"timestamp\":1472470996238000,\"value\":\"foo\"},{\"timestamp\":1472470996403000,\"value\":\"bar\"}],\"tags\":{\"clnt/finagle.version\":\"6.45.0\",\"http.path\":\"/api\"}}");
  }

  @Test public void span_shared_JSON_V2() {
    MutableSpan span = clientSpan;
    span.kind(Kind.SERVER);
    span.setShared();

    assertThat(new String(encoder.encode(clientSpan), UTF_8))
        .isEqualTo(
            "{\"traceId\":\"7180c278b62e8f6a216a2aea45d08fc9\",\"parentId\":\"6b221d5bc9e6496c\",\"id\":\"5b4185666d50f68b\",\"kind\":\"SERVER\",\"name\":\"get\",\"timestamp\":1472470996199000,\"duration\":207000,\"localEndpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"},\"remoteEndpoint\":{\"serviceName\":\"backend\",\"ipv4\":\"192.168.99.101\",\"port\":9000},\"annotations\":[{\"timestamp\":1472470996238000,\"value\":\"foo\"},{\"timestamp\":1472470996403000,\"value\":\"bar\"}],\"tags\":{\"clnt/finagle.version\":\"6.45.0\",\"http.path\":\"/api\"},\"shared\":true}");
  }

  @Test public void specialCharsInJson_JSON_V2() {
    assertThat(new String(encoder.encode(utf8Span), UTF_8))
        .isEqualTo(
            "{\"traceId\":\"0000000000000001\",\"id\":\"0000000000000001\",\"name\":\"\\\"\\\\\\t\\b\\n\\r\\f\",\"annotations\":[{\"timestamp\":1,\"value\":\"\\u2028 and \\u2029\"}],\"tags\":{\"\\\"foo\":\"Database error: ORA-00942:\\u2028 and \\u2029 table or view does not exist\\n\"}}");
  }

  @Test public void span_minimum_JSON_V2() {
    MutableSpan span = new MutableSpan();
    span.traceId("7180c278b62e8f6a216a2aea45d08fc9");
    span.id("5b4185666d50f68b");

    assertThat(new String(encoder.encode(span), UTF_8))
        .isEqualTo(
            "{\"traceId\":\"7180c278b62e8f6a216a2aea45d08fc9\",\"id\":\"5b4185666d50f68b\"}");
  }

  @Test public void span_noLocalServiceName_JSON_V2() {
    clientSpan.localServiceName(null);

    assertThat(new String(encoder.encode(clientSpan), UTF_8))
        .isEqualTo(
            "{\"traceId\":\"7180c278b62e8f6a216a2aea45d08fc9\",\"parentId\":\"6b221d5bc9e6496c\",\"id\":\"5b4185666d50f68b\",\"kind\":\"CLIENT\",\"name\":\"get\",\"timestamp\":1472470996199000,\"duration\":207000,\"localEndpoint\":{\"ipv4\":\"127.0.0.1\"},\"remoteEndpoint\":{\"serviceName\":\"backend\",\"ipv4\":\"192.168.99.101\",\"port\":9000},\"annotations\":[{\"timestamp\":1472470996238000,\"value\":\"foo\"},{\"timestamp\":1472470996403000,\"value\":\"bar\"}],\"tags\":{\"clnt/finagle.version\":\"6.45.0\",\"http.path\":\"/api\"}}");
  }

  @Test public void span_noRemoteServiceName_JSON_V2() {
    clientSpan.remoteServiceName(null);

    assertThat(new String(encoder.encode(clientSpan), UTF_8))
        .isEqualTo(
            "{\"traceId\":\"7180c278b62e8f6a216a2aea45d08fc9\",\"parentId\":\"6b221d5bc9e6496c\",\"id\":\"5b4185666d50f68b\",\"kind\":\"CLIENT\",\"name\":\"get\",\"timestamp\":1472470996199000,\"duration\":207000,\"localEndpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"},\"remoteEndpoint\":{\"ipv4\":\"192.168.99.101\",\"port\":9000},\"annotations\":[{\"timestamp\":1472470996238000,\"value\":\"foo\"},{\"timestamp\":1472470996403000,\"value\":\"bar\"}],\"tags\":{\"clnt/finagle.version\":\"6.45.0\",\"http.path\":\"/api\"}}");
  }

  @Test public void rootServerSpan_JSON_V2() {
    assertThat(new String(encoder.encode(rootServerSpan), UTF_8))
        .isEqualTo(
            "{\"traceId\":\"dc955a1d4768875d\",\"id\":\"dc955a1d4768875d\",\"kind\":\"SERVER\",\"name\":\"get\",\"timestamp\":1510256710021866,\"duration\":1117,\"localEndpoint\":{\"serviceName\":\"isao01\",\"ipv4\":\"10.23.14.72\"},\"tags\":{\"http.path\":\"/rs/A\",\"location\":\"T67792\",\"other\":\"A\"}}");
  }

  @Test public void rootServerSpan_JSON_V2_incomplete() {
    rootServerSpan.finishTimestamp(0L);

    assertThat(new String(encoder.encode(rootServerSpan), UTF_8))
        .isEqualTo(
            "{\"traceId\":\"dc955a1d4768875d\",\"id\":\"dc955a1d4768875d\",\"kind\":\"SERVER\",\"name\":\"get\",\"timestamp\":1510256710021866,\"localEndpoint\":{\"serviceName\":\"isao01\",\"ipv4\":\"10.23.14.72\"},\"tags\":{\"http.path\":\"/rs/A\",\"location\":\"T67792\",\"other\":\"A\"}}");
  }

  @Test public void rootServerSpan_JSON_V2_shared() {
    rootServerSpan.setShared();

    assertThat(new String(encoder.encode(rootServerSpan), UTF_8))
        .isEqualTo(
            "{\"traceId\":\"dc955a1d4768875d\",\"id\":\"dc955a1d4768875d\",\"kind\":\"SERVER\",\"name\":\"get\",\"timestamp\":1510256710021866,\"duration\":1117,\"localEndpoint\":{\"serviceName\":\"isao01\",\"ipv4\":\"10.23.14.72\"},\"tags\":{\"http.path\":\"/rs/A\",\"location\":\"T67792\",\"other\":\"A\"},\"shared\":true}");
  }
}
