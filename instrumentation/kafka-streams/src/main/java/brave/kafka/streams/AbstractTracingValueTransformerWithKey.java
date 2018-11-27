package brave.kafka.streams;

import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.processor.ProcessorContext;

abstract class AbstractTracingValueTransformerWithKey<K, V, VR> implements
    ValueTransformerWithKey<K, V, VR> {

  @Override public void init(ProcessorContext context) {
  }

  @Override public void close() {
  }
}
