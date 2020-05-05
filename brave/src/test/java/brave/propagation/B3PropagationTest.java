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
package brave.propagation;

import brave.Request;
import brave.Span;
import brave.internal.Platform;
import brave.propagation.B3Propagation.Format;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
// Added to declutter console: tells power mock not to mess with implicit classes we aren't testing
@PowerMockIgnore({"org.apache.logging.*", "javax.script.*"})
@PrepareForTest({Platform.class, B3Propagation.class})
public class B3PropagationTest {
  String traceIdHigh = "0000000000000009";
  String traceId = "0000000000000001";
  String parentId = "0000000000000002";
  String spanId = "0000000000000003";

  TraceContext context = TraceContext.newBuilder().traceId(1).parentId(2).spanId(3).build();

  Propagation<String> propagation = B3Propagation.B3_STRING;
  Platform platform = mock(Platform.class);

  @Before public void setupLogger() {
    mockStatic(Platform.class);
    when(Platform.get()).thenReturn(platform);
  }

  /** Either we asserted on the log messages or there weren't any */
  @After public void ensureNothingLogged() {
    verifyNoMoreInteractions(platform);
  }

  @Test public void keys_defaultToAll() {
    propagation = B3Propagation.newFactoryBuilder()
      .build().get();

    assertThat(propagation.keys()).containsExactly(
      "b3",
      "X-B3-TraceId",
      "X-B3-SpanId",
      "X-B3-ParentSpanId",
      "X-B3-Sampled",
      "X-B3-Flags"
    );
  }

  @Test public void keys_withoutB3Single() {
    propagation = B3Propagation.newFactoryBuilder()
      .injectFormat(Span.Kind.PRODUCER, Format.MULTI)
      .injectFormat(Span.Kind.CONSUMER, Format.MULTI)
      .build().get();

    assertThat(propagation.keys()).containsExactly(
      "X-B3-TraceId",
      "X-B3-SpanId",
      "X-B3-ParentSpanId",
      "X-B3-Sampled",
      "X-B3-Flags"
    );
  }

  @Test public void keys_onlyB3Single() {
    propagation = B3Propagation.newFactoryBuilder()
      .injectFormat(Format.SINGLE)
      .injectFormat(Span.Kind.CLIENT, Format.SINGLE)
      .injectFormat(Span.Kind.SERVER, Format.SINGLE)
      .build().get();

    assertThat(propagation.keys()).containsOnly("b3");
  }

  @Test public void injectFormat() {
    B3Propagation.Factory factory = (B3Propagation.Factory) B3Propagation.newFactoryBuilder()
      .injectFormat(Format.SINGLE)
      .build();

    assertThat(factory.injectFormats)
      .containsExactly(Format.SINGLE);
  }

  @Test public void injectKindFormat() {
    B3Propagation.Factory factory = (B3Propagation.Factory) B3Propagation.newFactoryBuilder()
      .injectFormat(Span.Kind.CLIENT, Format.SINGLE)
      .build();

    assertThat(factory.kindToInjectFormats.get(Span.Kind.CLIENT))
      .containsExactly(Format.SINGLE);
  }

  @Test public void injectKindFormats() {
    B3Propagation.Factory factory = (B3Propagation.Factory) B3Propagation.newFactoryBuilder()
      .injectFormats(Span.Kind.CLIENT, Format.SINGLE, Format.MULTI)
      .build();

    assertThat(factory.kindToInjectFormats.get(Span.Kind.CLIENT))
      .containsExactly(Format.SINGLE, Format.MULTI);
  }

  @Test public void injectKindFormats_cantBeSame() {
    assertThatThrownBy(() -> B3Propagation.newFactoryBuilder()
      .injectFormats(Span.Kind.CLIENT, Format.MULTI, Format.MULTI))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test public void injectKindFormats_cantBeBothSingle() {
    assertThatThrownBy(() -> B3Propagation.newFactoryBuilder()
      .injectFormats(Span.Kind.CLIENT, Format.SINGLE, Format.SINGLE_NO_PARENT))
      .isInstanceOf(IllegalArgumentException.class);
  }

  static class ClientRequest extends Request {
    final Map<String, String> headers = new LinkedHashMap<>();

    @Override public Span.Kind spanKind() {
      return Span.Kind.CLIENT;
    }

    @Override public Object unwrap() {
      return this;
    }

    void header(String key, String value) {
      headers.put(key, value);
    }
  }

  @Test public void clientUsesB3Multi() {
    ClientRequest request = new ClientRequest();
    Propagation.B3_STRING.injector(ClientRequest::header).inject(context, request);

    assertThat(request.headers)
      .hasSize(3)
      .containsEntry("X-B3-TraceId", "0000000000000001")
      .containsEntry("X-B3-ParentSpanId", "0000000000000002")
      .containsEntry("X-B3-SpanId", "0000000000000003");
  }

  static class ProducerRequest extends Request {
    final Map<String, String> headers = new LinkedHashMap<>();

    @Override public Span.Kind spanKind() {
      return Span.Kind.PRODUCER;
    }

    @Override public Object unwrap() {
      return this;
    }

    void header(String key, String value) {
      headers.put(key, value);
    }
  }

  @Test public void producerUsesB3SingleNoParent() {
    ProducerRequest request = new ProducerRequest();
    Propagation.B3_STRING.injector(ProducerRequest::header).inject(context, request);

    assertThat(request.headers)
      .hasSize(1)
      .containsEntry("b3", "0000000000000001-0000000000000003");
  }

  @Test public void canConfigureSingle() {
    propagation = B3Propagation.newFactoryBuilder()
      .injectFormat(Format.SINGLE_NO_PARENT)
      .build().get();

    Map<String, String> request = new LinkedHashMap<>(); // not a brave.Request
    propagation.<Map<String, String>>injector(Map::put).inject(context, request);

    assertThat(request)
      .hasSize(1)
      .containsEntry("b3", "0000000000000001-0000000000000003");
  }

  @Test public void canConfigureBasedOnKind() {
    propagation = B3Propagation.newFactoryBuilder()
      .injectFormats(Span.Kind.CLIENT, Format.SINGLE, Format.MULTI)
      .build().get();

    ClientRequest request = new ClientRequest();
    propagation.injector(ClientRequest::header).inject(context, request);

    assertThat(request.headers)
      .hasSize(4)
      .containsEntry("X-B3-TraceId", traceId)
      .containsEntry("X-B3-ParentSpanId", parentId)
      .containsEntry("X-B3-SpanId", spanId)
      .containsEntry("b3", traceId + "-" + spanId + "-" + parentId);
  }

  @Test public void extract_notYetSampled() {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("X-B3-TraceId", traceId);
    headers.put("X-B3-SpanId", spanId);

    assertThat(extract(headers).sampled()).isNull();
  }

  @Test public void extract_sampled() {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("X-B3-TraceId", traceId);
    headers.put("X-B3-SpanId", spanId);

    headers.put("X-B3-Sampled", "1");

    assertThat(extract(headers).sampled()).isTrue();

    headers.put("X-B3-Sampled", "true"); // old clients

    assertThat(extract(headers).sampled()).isTrue();
  }

  @Test public void extract_128Bit() {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("X-B3-TraceId", traceIdHigh + traceId);
    headers.put("X-B3-SpanId", spanId);

    assertThat(extract(headers).context()).isEqualToComparingFieldByField(TraceContext.newBuilder()
      .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16)).build()
    );
  }

  @Test public void extract_padded() {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("X-B3-TraceId", "0000000000000000" + traceId);
    headers.put("X-B3-SpanId", spanId);

    assertThat(extract(headers).context()).isEqualToComparingFieldByField(TraceContext.newBuilder()
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16)).build()
    );
  }

  @Test public void extract_padded_right() {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("X-B3-TraceId", traceIdHigh + "0000000000000000");
    headers.put("X-B3-SpanId", spanId);

    assertThat(extract(headers).context()).isEqualToComparingFieldByField(TraceContext.newBuilder()
      .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16)).build()
    );
  }

  @Test public void extract_zeros_traceId() {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("X-B3-TraceId", "0000000000000000");
    headers.put("X-B3-SpanId", spanId);

    assertThat(extract(headers).context()).isNull();

    verify(platform).log("Invalid input: traceId was all zeros", null);
  }

  @Test public void extract_zeros_traceId_128() {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("X-B3-TraceId", "00000000000000000000000000000000");
    headers.put("X-B3-SpanId", spanId);

    assertThat(extract(headers).context()).isNull();

    verify(platform).log("Invalid input: traceId was all zeros", null);
  }

  @Test public void extract_zeros_spanId() {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("X-B3-TraceId", traceId);
    headers.put("X-B3-SpanId", "0000000000000000");

    assertThat(extract(headers).context()).isNull();

    verify(platform).log("Invalid input: spanId was all zeros", null);
  }

  @Test public void extract_sampled_false() {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("X-B3-TraceId", traceId);
    headers.put("X-B3-SpanId", spanId);

    headers.put("X-B3-Sampled", "0");

    assertThat(extract(headers).sampled()).isFalse();

    headers.put("X-B3-Sampled", "false"); // old clients

    assertThat(extract(headers).sampled()).isFalse();
  }

  @Test public void extract_sampledCorrupt() {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("X-B3-TraceId", traceId);
    headers.put("X-B3-SpanId", spanId);

    Stream.of("", "d", "💩", "hello").forEach(sampled -> {
      headers.put("X-B3-Sampled", sampled);
      assertThat(extract(headers)).isSameAs(TraceContextOrSamplingFlags.EMPTY);

      verify(platform).log("Invalid input: expected 0 or 1 for X-B3-Sampled, but found '{0}'",
        sampled, null);
    });
  }

  TraceContextOrSamplingFlags extract(Map<String, String> headers) {
    return propagation.<Map<String, String>>extractor(Map::get).extract(headers);
  }
}
