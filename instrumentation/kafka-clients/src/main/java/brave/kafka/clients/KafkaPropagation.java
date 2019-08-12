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
package brave.kafka.clients;

import brave.propagation.Propagation.Getter;
import brave.propagation.Propagation.Setter;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Injector;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import static brave.propagation.B3SingleFormat.writeB3SingleFormat;
import static brave.propagation.B3SingleFormat.writeB3SingleFormatWithoutParentIdAsBytes;
import static java.nio.charset.StandardCharsets.UTF_8;

final class KafkaPropagation {
  static final TraceContext TEST_CONTEXT = TraceContext.newBuilder().traceId(1L).spanId(1L).build();
  static final ProducerRecord<String, String> TEST_RECORD =
    new ProducerRecord<>("dummy", "");
  static final Headers B3_SINGLE_TEST_HEADERS =
    TEST_RECORD.headers().add("b3", writeB3SingleFormat(TEST_CONTEXT).getBytes(UTF_8));

  static final Injector<Headers> B3_SINGLE_INJECTOR = new Injector<Headers>() {
    @Override public void inject(TraceContext traceContext, Headers carrier) {
      carrier.add("b3", writeB3SingleFormatWithoutParentIdAsBytes(traceContext));
    }

    @Override public String toString() {
      return "Headers::add(\"b3\",singleHeaderFormatWithoutParent)";
    }
  };

  static final Setter<Headers, String> SETTER = (carrier, key, value) -> {
    carrier.remove(key);
    carrier.add(key, value.getBytes(UTF_8));
  };

  static final Getter<Headers, String> GETTER = (carrier, key) -> {
    Header header = carrier.lastHeader(key);
    if (header == null || header.value() == null) return null;
    return new String(header.value(), UTF_8);
  };

  KafkaPropagation() {
  }
}
