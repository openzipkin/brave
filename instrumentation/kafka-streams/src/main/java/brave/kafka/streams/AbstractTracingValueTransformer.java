package brave.kafka.streams;

import org.apache.kafka.streams.kstream.ValueTransformer;
import org.apache.kafka.streams.processor.ProcessorContext;

abstract class AbstractTracingValueTransformer<V, VR> implements
    ValueTransformer<V, VR> {

  @Override public void init(ProcessorContext context) {
  }

  @Override public void close() {
  }
}
