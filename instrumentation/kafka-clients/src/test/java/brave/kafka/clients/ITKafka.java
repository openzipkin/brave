/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.kafka.clients;

import brave.messaging.MessagingTracing;
import brave.test.ITRemote;
import brave.test.util.AssertableCallback;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.RecordMetadata;

abstract class ITKafka extends ITRemote {
  MessagingTracing messagingTracing = MessagingTracing.create(tracing);
  KafkaTracing kafkaTracing = KafkaTracing.create(messagingTracing);

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
