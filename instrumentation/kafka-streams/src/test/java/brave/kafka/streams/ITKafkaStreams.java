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
package brave.kafka.streams;

import brave.messaging.MessagingTracing;
import brave.test.ITRemote;
import brave.test.util.AssertableCallback;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.RecordMetadata;

abstract class ITKafkaStreams extends ITRemote {
  MessagingTracing messagingTracing = MessagingTracing.create(tracing);
  KafkaStreamsTracing kafkaStreamsTracing = KafkaStreamsTracing.create(messagingTracing);

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
