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
package brave.propagation;

import brave.Request;
import brave.Span;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class B3PropagationTest {
  TraceContext context = TraceContext.newBuilder().traceId(1).parentId(2).spanId(3).build();

  static class ClientRequest extends Request {
    final Map<String, String> headers = new LinkedHashMap<>();

    @Override public Span.Kind kind() {
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

    @Override public Span.Kind kind() {
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
    Propagation<String> propagation = B3Propagation.newFactoryBuilder()
      .injectFormat(B3Propagation.Format.SINGLE_NO_PARENT)
      .build().create(Propagation.KeyFactory.STRING);

    Map<String, String> request = new LinkedHashMap<>(); // not a brave.Request
    propagation.<Map<String, String>>injector(Map::put).inject(context, request);

    assertThat(request)
      .hasSize(1)
      .containsEntry("b3", "0000000000000001-0000000000000003");
  }

  @Test public void canConfigureBasedOnKind() {
    Propagation<String> propagation = B3Propagation.newFactoryBuilder()
      .injectFormats(Span.Kind.CLIENT, B3Propagation.Format.SINGLE, B3Propagation.Format.MULTI)
      .build().create(Propagation.KeyFactory.STRING);

    ClientRequest request = new ClientRequest();
    propagation.injector(ClientRequest::header).inject(context, request);

    assertThat(request.headers)
      .hasSize(4)
      .containsEntry("X-B3-TraceId", "0000000000000001")
      .containsEntry("X-B3-ParentSpanId", "0000000000000002")
      .containsEntry("X-B3-SpanId", "0000000000000003")
      .containsEntry("b3", "0000000000000001-0000000000000003-0000000000000002");
  }
}
