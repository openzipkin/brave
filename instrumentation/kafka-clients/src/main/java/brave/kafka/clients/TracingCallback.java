package brave.kafka.clients;

import brave.Span;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.RecordMetadata;
import zipkin.internal.Nullable;

/**
 * Decorator which finish producer span.
 * Allows tracing to register the time between batching for send and actual send.
 */
class TracingCallback implements Callback {

  private final Span span;
  private final Callback wrappedCallback;

  TracingCallback(Span span, @Nullable Callback wrappedCallback) {
    this.span = span;
    this.wrappedCallback = wrappedCallback;
  }

  @Override public void onCompletion(RecordMetadata metadata, Exception exception) {
    if (exception != null) {
      // an error occurred, adding error to span
      this.span.tag("error", exception.getMessage());
    }
    this.span.finish();
    if (wrappedCallback != null) {
      this.wrappedCallback.onCompletion(metadata, exception);
    }
  }
}
