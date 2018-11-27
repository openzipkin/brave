package brave.kafka.streams;

import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;

abstract class AbstractTracingTransformer<K, V, R> implements
    Transformer<K, V, R> {

  @Override public void init(ProcessorContext context) {
  }

  @Override public void close() {
  }
}
