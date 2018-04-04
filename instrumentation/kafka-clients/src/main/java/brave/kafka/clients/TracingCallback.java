package brave.kafka.clients;

import brave.Span;
import brave.internal.Nullable;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.RecordMetadata;

/**
 * Decorator which finish producer span. Allows tracing to register the time between batching for
 * send and actual send.
 */
final class TracingCallback implements Callback {

  final Span span;
  @Nullable final Callback wrappedCallback;

  TracingCallback(Span span, @Nullable Callback wrappedCallback) {
    this.span = span;
    this.wrappedCallback = wrappedCallback;
  }

  @Override public void onCompletion(RecordMetadata metadata, @Nullable Exception exception) {
    if (exception != null) span.error(exception);
    span.finish();
    if (wrappedCallback != null) {
      wrappedCallback.onCompletion(metadata, exception);
    }
  }
}
