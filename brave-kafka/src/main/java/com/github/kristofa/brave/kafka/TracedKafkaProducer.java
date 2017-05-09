package com.github.kristofa.brave.kafka;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.SpanId;
import com.twitter.zipkin.gen.Span;
import java.io.Closeable;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.Future;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import zipkin.Constants;

public final class TracedKafkaProducer implements Closeable {
  final Brave brave;
  final Producer<SpanId, byte[]> delegate;

  TracedKafkaProducer(Brave brave, Properties properties) {
    Properties zipkinProperties = new Properties();
    zipkinProperties.putAll(properties);
    zipkinProperties.put("key.serializer", SpanIdSerializer.class.getName());
    zipkinProperties.put("value.serializer", ByteArraySerializer.class.getName());
    this.brave = brave;
    this.delegate = new KafkaProducer<SpanId, byte[]>(zipkinProperties);
  }

  Future<RecordMetadata> send(String topic, byte[] value) {
    SpanId spanId = brave.localTracer().startNewSpan("", topic);
    if (spanId == null) { // send directly
      return delegate.send(new ProducerRecord<SpanId, byte[]>(topic, value));
    }
    // clear local component as we are abusing local tracer as a messaging tracer
    brave.localSpanThreadBinder().getCurrentLocalSpan().getBinary_annotations().clear();

    brave.localTracer().submitAnnotation("ms");
    brave.localTracer().submitBinaryAnnotation("kafka.topic", topic);
    final Span span = brave.localSpanThreadBinder().getCurrentLocalSpan();
    brave.localSpanThreadBinder().setCurrentSpan(null);

    return delegate.send(new ProducerRecord<SpanId, byte[]>(topic, spanId, value), new Callback() {
      @Override public void onCompletion(RecordMetadata recordMetadata, Exception e) {
        brave.localSpanThreadBinder().setCurrentSpan(span);
        if (e != null) {
          brave.localTracer().submitBinaryAnnotation(Constants.ERROR, e.getMessage());
        }
        brave.localTracer().submitAnnotation(Constants.WIRE_SEND);
        brave.localTracer().finishSpan(0); // flush the span: no more work will happen on this side
      }
    });
  }

  @Override public void close() throws IOException {
    delegate.close();
  }
}
