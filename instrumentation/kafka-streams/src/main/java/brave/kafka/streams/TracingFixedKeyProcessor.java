/*
 * Copyright 2013-2024 The OpenZipkin Authors
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
package brave.kafka.streams;

import org.apache.kafka.common.header.Headers;
import org.apache.kafka.streams.processor.api.FixedKeyProcessor;
import org.apache.kafka.streams.processor.api.FixedKeyProcessorContext;
import org.apache.kafka.streams.processor.api.FixedKeyRecord;

class TracingFixedKeyProcessor<KIn, VIn, VOut> extends
  BaseTracingProcessor<FixedKeyProcessorContext<KIn, VOut>, FixedKeyRecord<KIn, VIn>, FixedKeyProcessor<KIn, VIn, VOut>>
  implements FixedKeyProcessor<KIn, VIn, VOut> {

  TracingFixedKeyProcessor(KafkaStreamsTracing kafkaStreamsTracing, String spanName,
    FixedKeyProcessor<KIn, VIn, VOut> delegate) {
    super(kafkaStreamsTracing, spanName, delegate);
  }

  @Override Headers headers(FixedKeyRecord<KIn, VIn> record) {
    return record.headers();
  }

  @Override
  void process(FixedKeyProcessor<KIn, VIn, VOut> delegate, FixedKeyRecord<KIn, VIn> record) {
    delegate.process(record);
  }

  @Override public void init(FixedKeyProcessorContext<KIn, VOut> context) {
    this.context = context;
    delegate.init(context);
  }

  @Override public void close() {
    delegate.close();
  }
}
