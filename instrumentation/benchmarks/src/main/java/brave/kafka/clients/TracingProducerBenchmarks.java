package brave.kafka.clients;

import brave.Tracing;
import com.google.common.util.concurrent.Futures;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import zipkin2.reporter.Reporter;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class TracingProducerBenchmarks {
  ProducerRecord<String, String> record = new ProducerRecord<>("topic", "key", "value");
  Producer<String, String> producer, tracingProducer, tracingB3SingleProducer;

  @Setup(Level.Trial) public void init() {
    Tracing tracing = Tracing.newBuilder().spanReporter(Reporter.NOOP).build();
    producer = new FakeProducer();
    tracingProducer = KafkaTracing.create(tracing).producer(producer);
    tracingB3SingleProducer =
        KafkaTracing.newBuilder(tracing).writeB3SingleFormat(true).build().producer(producer);
  }

  @TearDown(Level.Trial) public void close() {
    Tracing.current().close();
  }

  @Benchmark public RecordMetadata send_baseCase() throws Exception {
    return producer.send(record).get();
  }

  @Benchmark public RecordMetadata send_traced() throws Exception {
    return tracingProducer.send(record).get();
  }

  @Benchmark public RecordMetadata send_traced_b3Single() throws Exception {
    return tracingB3SingleProducer.send(record).get();
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
        .addProfiler("gc")
        .include(".*" + TracingProducerBenchmarks.class.getSimpleName())
        .build();

    new Runner(opt).run();
  }

  static final class FakeProducer implements Producer<String, String> {
    @Override public void initTransactions() {
    }

    @Override public void beginTransaction() {
    }

    @Override
    public void sendOffsetsToTransaction(Map<TopicPartition, OffsetAndMetadata> map, String s) {
    }

    @Override public void commitTransaction() {
    }

    @Override public void abortTransaction() {
    }

    @Override public Future<RecordMetadata> send(ProducerRecord<String, String> record) {
      return send(record, null);
    }

    @Override
    public Future<RecordMetadata> send(ProducerRecord<String, String> record, Callback callback) {
      TopicPartition tp = new TopicPartition(record.topic(), 0);
      RecordMetadata rm = new RecordMetadata(tp, -1L, -1L, 1L, 2L, 3, 4);
      if (callback != null) callback.onCompletion(rm, null);
      return Futures.immediateFuture(rm);
    }

    @Override public void flush() {
    }

    @Override public List<PartitionInfo> partitionsFor(String s) {
      return null;
    }

    @Override public Map<MetricName, ? extends Metric> metrics() {
      return null;
    }

    @Override public void close() {
    }

    @Override public void close(long l, TimeUnit timeUnit) {
    }

    @Override public void close(Duration duration) {
    }
  }
}
