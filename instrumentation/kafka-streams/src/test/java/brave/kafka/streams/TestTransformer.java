package brave.kafka.streams;

import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;

public abstract class TestTransformer<R> implements Transformer<String, String, R> {
    ProcessorContext context;

    @Override
    public void init(ProcessorContext context) {
      this.context = context;
    }

    @Override
    public void close() {
    }
  }
