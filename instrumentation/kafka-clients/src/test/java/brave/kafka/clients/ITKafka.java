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
package brave.kafka.clients;

import brave.messaging.MessagingTracing;
import brave.propagation.Propagation;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.test.ITRemote;
import brave.test.util.AssertableCallback;
import com.google.common.base.Charsets;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

abstract class ITKafka extends ITRemote {
  String TEST_TOPIC = "myTopic";
  String TEST_KEY = "foo";
  String TEST_VALUE = "bar";

  ConsumerRecord<String, String> fakeRecord =
    new ConsumerRecord<>(TEST_TOPIC, 0, 1L, TEST_KEY, TEST_VALUE);

  MessagingTracing messagingTracing = MessagingTracing.create(tracing);
  KafkaTracing kafkaTracing = KafkaTracing.create(messagingTracing);
  TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);

  <K, V> void addB3MultiHeaders(ConsumerRecord<K, V> record) {
    Propagation.B3_STRING.injector(KafkaPropagation.SETTER).inject(parent, record.headers());
  }

  static Set<Map.Entry<String, String>> lastHeaders(Headers headers) {
    Map<String, String> result = new LinkedHashMap<>();
    headers.forEach(h -> result.put(h.key(), new String(h.value(), Charsets.UTF_8)));
    return result.entrySet();
  }

  static Map<String, String> lastHeaders(MockProducer<Object, String> mockProducer) {
    Map<String, String> headers = new LinkedHashMap<>();
    List<ProducerRecord<Object, String>> history = mockProducer.history();
    ProducerRecord<Object, String> lastRecord = history.get(history.size() - 1);
    for (Header header : lastRecord.headers()) {
      headers.put(header.key(), new String(header.value(), StandardCharsets.UTF_8));
    }
    return headers;
  }

  /** {@link #join()} waits for the callback to complete without any errors */
  static final class BlockingCallback implements Callback {
    final AssertableCallback<RecordMetadata> delegate = new AssertableCallback<>();

    void join() {
      delegate.join();
    }

    @Override public void onCompletion(RecordMetadata metadata, Exception exception) {
      if (exception != null) {
        delegate.onError(exception);
      } else {
        delegate.onSuccess(metadata);
      }
    }
  }
}
