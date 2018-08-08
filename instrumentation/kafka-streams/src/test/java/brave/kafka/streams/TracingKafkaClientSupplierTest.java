package brave.kafka.streams;

import brave.Tracing;
import brave.kafka.clients.KafkaTracing;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.assertNotNull;

public class TracingKafkaClientSupplierTest {

  private Tracing tracing = Tracing.newBuilder().build();
  private KafkaTracing kafkaTracing = KafkaTracing.newBuilder(tracing).build();
  private TracingKafkaClientSupplier supplier = new TracingKafkaClientSupplier(kafkaTracing);

  @Test
  public void shouldSupplyTracingProducer() {
    //Given that properties only contains bootstrap servers
    final HashMap<String, Object> config = new HashMap<>();
    config.put("bootstrap.servers", "localhost:9092");
    //When Supplier is invoked
    final Producer<byte[], byte[]> producer = supplier.getProducer(config);
    //Then producer is valid
    assertNotNull(producer);
  }

  @Test
  public void shouldSupplyTracingConsumer() {
    //Given that properties only contains bootstrap servers
    final HashMap<String, Object> config = new HashMap<>();
    config.put("bootstrap.servers", "localhost:9092");
    //When Supplier is invoked
    final Consumer<byte[], byte[]> producer = supplier.getConsumer(config);
    //Then producer is valid
    assertNotNull(producer);
  }
}