package com.github.kristofa.brave.kafka;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.SpanId;
import com.twitter.zipkin.gen.Span;
import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import kafka.consumer.Consumer;
import kafka.consumer.ConsumerConfig;
import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;
import kafka.javaapi.consumer.ConsumerConnector;
import kafka.message.MessageAndMetadata;
import zipkin.Constants;

public final class TracedKafkaConsumer implements Closeable {
  final Brave brave;
  final ConsumerConnector delegate;

  TracedKafkaConsumer(Brave brave, Properties properties) {
    this.brave = brave;
    this.delegate = Consumer.createJavaConsumerConnector(new ConsumerConfig(properties));
  }

  Iterator<byte[]> iterator(String topic) {
    Map<String, Integer> topicCountMap = new HashMap<>();
    topicCountMap.put(topic, 1);
    final KafkaStream<byte[], byte[]> stream = delegate.createMessageStreams(topicCountMap)
        .values()
        .iterator()
        .next()
        .get(0);

    return new Iterator<byte[]>() {
      ConsumerIterator<byte[], byte[]> delegate = stream.iterator();

      @Override public boolean hasNext() {
        return delegate.hasNext();
      }

      @Override public byte[] next() {
        MessageAndMetadata<byte[], byte[]> result = delegate.next();
        Span span = SpanId.fromBytes(result.key()).toSpan();
        span.setName(result.topic());
        brave.localSpanThreadBinder().setCurrentSpan(span);
        try {
          brave.localTracer().submitAnnotation(Constants.WIRE_RECV);
          brave.localTracer().submitAnnotation("mr");
          brave.localTracer().submitBinaryAnnotation("kafka.partition", result.partition());

          return result.message();
        } finally {
          brave.localTracer().finishSpan(0); // flush (which completes) the messaging span

          // Note: we aren't associating the span that just finished as a thread context
          // This is potentially a dual-parent problem. Imagine you are a consumer of this iterator.
          // You may have already been in a trace prior to calling next(). Since zipkin doesn't
          // support dual parents we have a choice to either ignore the trace that resulted in this
          // message, or use its trace id as the current trace id.
          //
          // Ex. existing trace calls iterator.next() on a kafka producer-backed iterator
          // This is a join. We could log the joining trace id into the message-trace, or visa-versa
          //
          // Ex. no existing trace calls iterator.next() on a kafka producer-backed iterator
          // We could choose to simply set the Brave's current server span to the span that just finished.
          // This would allow future local or client calls to appear caused by the message producer

          // brave.serverSpanThreadBinder().setCurrentSpan(span);// doesn't work as not public visible
        }
      }
    };
  }

  @Override public void close() throws IOException {
    delegate.shutdown();
  }
}
